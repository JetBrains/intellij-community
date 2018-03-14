package com.intellij.util.plist;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Dictionary implements Map<String, Object> {
  private static final NotNullProducer<Map<String, Object>> DEFAULT_PRODUCER = () -> new LinkedHashMap<>();

  private static final NotNullProducer<Map<String, Object>> EMPTY_PRODUCER = () -> Collections.emptyMap();

  public static final Dictionary EMPTY_DICTIONARY = new Dictionary(true);

  private final NotNullProducer<Map<String, Object>> myMapFactory;
  private final Map<String, Object> myAttributes;

  public Dictionary() {
    this(DEFAULT_PRODUCER);
  }

  public Dictionary(boolean emptyFlag) {
    this(EMPTY_PRODUCER);
  }

  public Dictionary(@NotNull NotNullProducer<Map<String, Object>> mapFactory) {
    myMapFactory = mapFactory;
    myAttributes = createMap();
  }

  private Map<String, Object> createMap() {
    return myMapFactory.produce();
  }

  @Override
  public boolean isEmpty() {
    return myAttributes.isEmpty();
  }

  @Override
  public int size() {
    return myAttributes.size();
  }

  @Override
  public boolean containsKey(Object key) {
    return myAttributes.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return myAttributes.containsValue(value);
  }

  @Override
  public Object put(String key, Object value) {
    return put(key, value, true);
  }

  public Object put(String key, Object value, boolean checkValue) {
    if (checkValue) {
      checkAttribute(value);
    }
    return myAttributes.put(key, value);
  }

  protected void checkAttribute(Object value) {
    if (!isValidAttribute(value)) {
      String message = "Unsupported attribute: ";
      if (value != null) message += "(" + value.getClass().getName() + ")";
      message += String.valueOf(value);
      throw new IllegalArgumentException(message);
    }

    if (value instanceof Collection) {
      for (Object each : (Collection)value) {
        checkAttribute(each);
      }
    }
  }

  protected boolean isValidAttribute(@Nullable Object value) {
    return true;
  }

  private void checkMap(Map<? extends String, ?> m) {
    for (Object each : m.values()) {
      checkAttribute(each);
    }
  }

  @Override
  public Object remove(Object key) {
    return myAttributes.remove(key);
  }

  @Override
  public void putAll(@NotNull Map<? extends String, ?> m) {
    checkMap(m);
    myAttributes.putAll(m);
  }

  public void setAttribute(@NotNull String key, @Nullable Object value) {
    setAttribute(key, value, true);
  }

  public void setAttribute(@NotNull String key, @Nullable Object value, boolean checkValue) {
    if (value == null) {
      remove(key);
    }
    else {
      put(key, value, checkValue);
    }
  }

  @Override
  public void clear() {
    myAttributes.clear();
  }

  @Override
  @Nullable
  public Object get(@NotNull Object key) {
    return myAttributes.get(key);
  }

  public Dictionary getDictionary(@NotNull String key, @Nullable Dictionary defValue) {
    return getAttribute(key, Dictionary.class, defValue);
  }

  @Nullable
  public String getString(@NotNull String key) {
    return getString(key, null);
  }

  public String getString(@NotNull String key, @Nullable String defValue) {
    Object result = get(key);
    if (result == null) return defValue;
    if (result instanceof String) return (String)result;

    return checked(result, String.class);
  }

  @NotNull
  public String getNotNullString(@NotNull String key) {
    return notNull(key, getString(key));
  }

  @Nullable
  public <T> T getAttribute(@NotNull String key, @Nullable Class<T> clazz) {
    return getAttribute(key, clazz, null);
  }

  @SuppressWarnings({"ConstantConditions"})
  public <T> T getAttribute(@NotNull String key, @Nullable Class<T> clazz, @Nullable T defValue) {
    T result = checked(get(key), clazz);
    return result == null ? defValue : result;
  }

  @NotNull
  public <T> T getNotNullAttribute(@NotNull String key, @Nullable Class<T> clazz) {
    return notNull(key, getAttribute(key, clazz));
  }

  public <T> List<T> getAttributeList(@NotNull String key, @Nullable Class<T> clazz, @Nullable List<T> defValue) {
    List result = getAttribute(key, List.class, defValue);
    if (result != null && clazz != null) {
      for (Object o : result) {
        checked(o, clazz);
      }
    }
    //noinspection unchecked, ConstantConditions
    return result;
  }

  @NotNull
  public <T> List<T> getObjects(@Nullable Class<T> clazz) {
    List<T> result = new SmartList<>();
    for (Object each : myAttributes.values()) {
      if (clazz == null || clazz.isInstance(each)) {
        result.add((T)each);
      }
    }
    return result;
  }

  @Override
  public Set<String> keySet() {
    return myAttributes.keySet();
  }

  @Override
  public Collection<Object> values() {
    return myAttributes.values();
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return myAttributes.entrySet();
  }

  @Override
  public String toString() {
    return doGetDebugString(this, "");
  }

  private static String doGetDebugString(Object object, final String indent) {
    if (object instanceof Dictionary) {
      Map<String, Object> attributes = ((Dictionary)object).myAttributes;
      if (attributes.isEmpty()) return "{ }";
      return "{\n" + StringUtil.join(attributes.entrySet(),
                                     each -> indent + " " + each.getKey() + " = " + doGetDebugString(each.getValue(), indent + " "), "\n") + "\n" + indent + "}";
    }
    else {
      return object.toString();
    }
  }

  @Override
  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  public boolean equals(Object obj) {
    return myAttributes.equals(obj);
  }

  @Override
  public int hashCode() {
    return myAttributes.hashCode();
  }

  @NotNull
  protected <T> T notNull(@NotNull String key, @Nullable T obj) {
    if (obj == null) {
      throw new InvalidSpecException("Object '" + key + "' not found in " + toString());
    }
    return obj;
  }

  public <T> T checked(@Nullable Object obj, @Nullable Class<T> clazz) {
    return checked(this, obj, clazz);
  }

  public static <T> T checked(@Nullable Dictionary self, @Nullable Object obj, @Nullable Class<T> clazz) {
    if (obj != null && clazz != null && !clazz.isInstance(obj)) {
      Logger.getInstance(Dictionary.class).error("Cannot cast object " + obj.getClass()
                    + " to " + clazz
                    + "\n" + obj.toString()
                    + "from:\n" + self);
      return null;
    }
    //noinspection unchecked
    return (T)obj;
  }
}

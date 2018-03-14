package com.intellij.util.plist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class Plist extends Dictionary {
  public static final Plist EMPTY_PLIST = new Plist(true);

  public Plist() {
  }

  private Plist(boolean empty) {
    super(true);
  }

  protected boolean isValidAttribute(Object value) {
    return value == null
           || value instanceof String
           || value instanceof Number
           || value instanceof Boolean
           || value instanceof Date
           || value instanceof List
           || value instanceof Plist;
  }

  @Nullable
  public Boolean getBoolean(@NotNull String key) {
    return getBoolean(key, null);
  }

  @SuppressWarnings({"ConstantConditions"})
  public Boolean getBoolean(@NotNull String key, @Nullable Boolean defValue) {
    Object obj = getAttribute(key, Object.class);

    if (obj instanceof String) {
      String val = (String)obj;
      if ("true".equalsIgnoreCase(val) || "yes".equalsIgnoreCase(val)) return true;
      if ("false".equalsIgnoreCase(val) || "no".equalsIgnoreCase(val)) return false;
    }
    else if (obj instanceof Number) {
      return ((Number)obj).intValue() > 0;
    }
    return obj == null ? defValue : checked(obj, Boolean.class);
  }

  @NotNull
  public Boolean getNotNullBoolean(@NotNull String key) {
    return notNull(key, getBoolean(key));
  }

  @Nullable
  public Long getInteger(@NotNull String key) {
    return getInteger(key, null);
  }

  public Long getInteger(@NotNull String key, @Nullable Integer defValue) {
    Object obj = getAttribute(key, Object.class);
    if (obj instanceof String) {
      try {
        Integer.parseInt((String)obj);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return obj == null ? defValue == null ? null : Long.valueOf(defValue.longValue()) : checked(obj, Long.class);
  }

  @NotNull
  public Long getNotNullInteger(@NotNull String key) {
    return notNull(key, getInteger(key));
  }

  @Nullable
  public Double getFloat(@NotNull String key) {
    return getFloat(key, null);
  }

  public Double getFloat(@NotNull String key, @Nullable Double defValue) {
    Object obj = getAttribute(key, Object.class);
    if (obj instanceof String) {
      try {
        Double.parseDouble((String)obj);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return obj == null ? defValue : checked(obj, Double.class);
  }

  @NotNull
  public Double getNotNullFloat(@NotNull String key) {
    return notNull(key, getFloat(key));
  }
}

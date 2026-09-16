// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Image;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * see <a href="http://developer.apple.com/documentation/Cocoa/Reference/ObjCRuntimeRef/Reference/reference.html">Documentation</a>
 */
public final @NonNls class Foundation {
  public static void init() {
    FoundationNative.init();
  }

  public static boolean isAvailable() {
    if (!SystemInfoRt.isMac) return false;
    try {
      init();
      return true;
    }
    catch (LinkageError | RuntimeException ignored) {
      return false;
    }
  }

  private static ID nativeID(Object value) {
    return value instanceof MemorySegment segment
           ? new ID(segment.address())
           : value == null ? ID.NIL : new ID(((Number)value).longValue());
  }

  private Foundation() { }

  /**
   * Get the ID of the NSClass with className
   */
  public static ID getObjcClass(String className) {
    return nativeID(FoundationNative.call("objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS), className));
  }

  public static ID getProtocol(String name) {
    return nativeID(FoundationNative.call("objc_getProtocol", FunctionDescriptor.of(ADDRESS, ADDRESS), name));
  }

  public static Selector createSelector(String name) {
    var value = (MemorySegment)FoundationNative.call("sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS), name);
    return new Selector(name, value.address());
  }

  public static @NotNull ID invoke(final ID id, final Selector selector, Object... args) {
    return nativeID(FoundationNative.invoke(id, selector, args));
  }

  public static ID invoke(final String cls, final String selector, Object... args) {
    return invoke(getObjcClass(cls), createSelector(selector), args);
  }

  public static ID safeInvoke(final String stringCls, final String stringSelector, Object... args) {
    ID cls = getObjcClass(stringCls);
    Selector selector = createSelector(stringSelector);
    if (!invoke(cls, "respondsToSelector:", selector).booleanValue()) {
      throw new RuntimeException(String.format("Missing selector %s for %s", stringSelector, stringCls));
    }
    return invoke(cls, selector, args);
  }

  public static @NotNull ID invoke(final ID id, final String selector, Object... args) {
    return invoke(id, createSelector(selector), args);
  }

  public static double invoke_fpret(ID receiver, Selector selector, Object... args) {
    var result = FoundationNative.invoke(receiver, selector, args);
    return result == null ? 0 : ((Number)result).doubleValue();
  }

  public static double invoke_fpret(ID receiver, String selector, Object... args) {
    return invoke_fpret(receiver, createSelector(selector), args);
  }

  public static boolean isNil(ID id) {
    return id == null || ID.NIL.equals(id);
  }

  public static ID safeInvoke(final ID id, final String stringSelector, Object... args) {
    Selector selector = createSelector(stringSelector);
    if (!id.equals(ID.NIL) && !invoke(id, "respondsToSelector:", selector).booleanValue()) {
      throw new RuntimeException(String.format("Missing selector %s for %s", stringSelector, toStringViaUTF8(invoke(id, "description"))));
    }
    return invoke(id, selector, args);
  }

  public static ID allocateObjcClassPair(ID superCls, String name) {
    return nativeID(
      FoundationNative.call("objc_allocateClassPair", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), superCls, name, 0L));
  }

  public static void registerObjcClassPair(ID cls) {
    FoundationNative.call("objc_registerClassPair", FunctionDescriptor.ofVoid(ADDRESS), cls);
  }

  public static boolean isClassRespondsToSelector(ID cls, Selector selectorName) {
    return (byte)FoundationNative.call("class_respondsToSelector", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS), cls, selectorName) !=
           0;
  }

  /**
   * @param cls          The class to which to add a method.
   * @param selectorName A selector that specifies the name of the method being added.
   * @param impl         A function which is the implementation of the new method. The function must take at least two arguments-self and _cmd.
   * @param types        An array of characters that describe the types of the arguments to the method.
   *                     See <a href="https://developer.apple.com/library/IOs/documentation/Cocoa/Conceptual/ObjCRuntimeGuide/Articles/ocrtTypeEncodings.html#//apple_ref/doc/uid/TP40008048-CH100"></a>
   * @return true if the method was added successfully, otherwise false (for example, the class already contains a method implementation with that name).
   */
  public static boolean addMethod(ID cls, Selector selectorName, MethodHandle impl, String types) {
    return addMethodByID(cls, selectorName, new ID(createNativeCallback(impl, types, Arena.global()).address()), types);
  }

  public static MemorySegment createNativeCallback(MethodHandle target, String types, Arena arena) {
    return FoundationNative.callback(target, types, arena);
  }

  public static MethodHandle callback(Object target, String methodName, Class<?>... parameterTypes) {
    var owner = target instanceof Class<?> targetClass ? targetClass : target.getClass();
    try {
      var methods = Arrays.stream(owner.getDeclaredMethods()).filter(method -> method.getName().equals(methodName)).toList();
      var method =
        parameterTypes.length == 0 && methods.size() == 1 ? methods.getFirst() : owner.getDeclaredMethod(methodName, parameterTypes);
      var handle = MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).unreflect(method);
      return target instanceof Class<?> ? handle : handle.bindTo(target);
    }
    catch (ReflectiveOperationException exception) {
      throw new IllegalArgumentException("Cannot bind the native callback: " + methodName, exception);
    }
  }

  public static boolean addProtocol(ID aClass, ID protocol) {
    return (byte)FoundationNative.call("class_addProtocol", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS), aClass, protocol) != 0;
  }

  public static boolean addMethodByID(ID cls, Selector selectorName, ID impl, String types) {
    return (byte)FoundationNative.call("class_addMethod", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS), cls,
                                       selectorName, impl, types) != 0;
  }

  public static boolean isMetaClass(ID cls) {
    return (byte)FoundationNative.call("class_isMetaClass", FunctionDescriptor.of(JAVA_BYTE, ADDRESS), cls) != 0;
  }

  public static @Nullable String stringFromSelector(Selector selector) {
    ID id = nativeID(FoundationNative.call("NSStringFromSelector", FunctionDescriptor.of(ADDRESS, ADDRESS), selector));
    return ID.NIL.equals(id) ? null : toStringViaUTF8(id);
  }

  public static @Nullable String stringFromClass(ID aClass) {
    ID id = nativeID(FoundationNative.call("NSStringFromClass", FunctionDescriptor.of(ADDRESS, ADDRESS), aClass));
    return ID.NIL.equals(id) ? null : toStringViaUTF8(id);
  }

  public static MemorySegment getClass(MemorySegment clazz) {
    return (MemorySegment)FoundationNative.call("objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS), clazz);
  }

  public static String fullUserName() {
    return toStringViaUTF8(nativeID(FoundationNative.call("NSFullUserName", FunctionDescriptor.of(ADDRESS))));
  }

  public static ID class_replaceMethod(ID cls, Selector selector, MethodHandle impl, String types) {
    return nativeID(FoundationNative.call("class_replaceMethod", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                                          cls, selector, createNativeCallback(impl, types, Arena.global()), types));
  }

  public static ID getMetaClass(String className) {
    return nativeID(FoundationNative.call("objc_getMetaClass", FunctionDescriptor.of(ADDRESS, ADDRESS), className));
  }

  public static boolean isPackageAtPath(final @NotNull String path) {
    final ID workspace = invoke("NSWorkspace", "sharedWorkspace");
    final ID result = invoke(workspace, createSelector("isFilePackageAtPath:"), nsString(path));

    return result.booleanValue();
  }

  public static boolean isPackageAtPath(final @NotNull File file) {
    if (!file.isDirectory()) return false;
    return isPackageAtPath(file.getPath());
  }

  private static final class NSString {
    private static final ID nsStringCls = getObjcClass("NSString");
    private static final Selector stringSel = createSelector("string");
    private static final Selector allocSel = createSelector("alloc");
    private static final Selector autoreleaseSel = createSelector("autorelease");
    private static final Selector initWithBytesLengthEncodingSel = createSelector("initWithBytes:length:encoding:");
    private static final long nsEncodingUTF16LE = convertCFEncodingToNS(FoundationLibrary.kCFStringEncodingUTF16LE);

    public static @NotNull ID create(@NotNull String s) {
      if (s.isEmpty()) {
        return invoke(nsStringCls, stringSel);
      }

      byte[] utf16Bytes = s.getBytes(StandardCharsets.UTF_16LE);
      return create(utf16Bytes);
    }

    public static @NotNull ID create(@NotNull CharSequence cs) {
      if (cs instanceof String s) {
        return create(s);
      }
      if (cs.isEmpty()) {
        return invoke(nsStringCls, stringSel);
      }

      var buffer = StandardCharsets.UTF_16LE.encode(CharBuffer.wrap(cs));
      byte[] utf16Bytes = new byte[buffer.remaining()];
      buffer.get(utf16Bytes);
      return create(utf16Bytes);
    }

    private static @NotNull ID create(byte[] utf16Bytes) {
      ID emptyNsString = invoke(nsStringCls, allocSel);
      ID initializedNsString = invoke(emptyNsString, initWithBytesLengthEncodingSel, utf16Bytes, utf16Bytes.length, nsEncodingUTF16LE);
      return invoke(initializedNsString, autoreleaseSel);
    }
  }

  public static @NotNull ID nsString(@Nullable String s) {
    return s == null ? ID.NIL : NSString.create(s);
  }

  public static @NotNull ID nsString(@Nullable CharSequence s) {
    return s == null ? ID.NIL : NSString.create(s);
  }

  public static ID nsUUID(@NotNull UUID uuid) {
    return nsUUID(uuid.toString());
  }

  public static ID nsUUID(@NotNull String uuid) {
    return invoke(invoke(invoke("NSUUID", "alloc"), "initWithUUIDString:", nsString(uuid)), "autorelease");
  }

  public static @Nullable String toStringViaUTF8(ID cfString) {
    if (ID.NIL.equals(cfString)) return null;

    long length = (long)FoundationNative.call("CFStringGetLength", FunctionDescriptor.of(JAVA_LONG, ADDRESS), cfString);
    if (length == 0) return "";
    try (var arena = Arena.ofConfined()) {
      var rangeLayout = MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG);
      var range = arena.allocate(rangeLayout);
      range.set(JAVA_LONG, 0, 0L);
      range.set(JAVA_LONG, JAVA_LONG.byteSize(), length);
      var buffer = arena.allocate(JAVA_CHAR, Math.toIntExact(length));
      FoundationNative.call("CFStringGetCharacters", FunctionDescriptor.ofVoid(ADDRESS, rangeLayout, ADDRESS), cfString, range, buffer);
      return new String(buffer.toArray(JAVA_CHAR));
    }
  }

  public static @NlsSafe @Nullable String getNSErrorText(@Nullable ID error) {
    if (isNil(error)) return null;

    String description = toStringViaUTF8(invoke(error, "localizedDescription"));
    String recovery = toStringViaUTF8(invoke(error, "localizedRecoverySuggestion"));
    if (recovery != null) description += "\n" + recovery;
    return StringUtil.notNullize(description);
  }

  public static @Nullable String getEncodingName(long nsStringEncoding) {
    int cfEncoding =
      (int)FoundationNative.call("CFStringConvertNSStringEncodingToEncoding", FunctionDescriptor.of(JAVA_INT, JAVA_LONG), nsStringEncoding);
    ID pointer =
      nativeID(FoundationNative.call("CFStringConvertEncodingToIANACharSetName", FunctionDescriptor.of(ADDRESS, JAVA_INT), cfEncoding));
    String name = toStringViaUTF8(pointer);
    if ("macintosh".equals(name)) name = "MacRoman"; // JDK8 does not recognize IANA's "macintosh" alias
    return name;
  }

  public static long getEncodingCode(@Nullable String encodingName) {
    if (StringUtil.isEmptyOrSpaces(encodingName)) return -1;

    ID converted = nsString(encodingName);
    int cfEncoding =
      (int)FoundationNative.call("CFStringConvertIANACharSetNameToEncoding", FunctionDescriptor.of(JAVA_INT, ADDRESS), converted);

    ID restored =
      nativeID(FoundationNative.call("CFStringConvertEncodingToIANACharSetName", FunctionDescriptor.of(ADDRESS, JAVA_INT), cfEncoding));
    if (ID.NIL.equals(restored)) return -1;

    return convertCFEncodingToNS(cfEncoding);
  }

  private static long convertCFEncodingToNS(long cfEncoding) {
    return (long)FoundationNative.call("CFStringConvertEncodingToNSStringEncoding", FunctionDescriptor.of(JAVA_LONG, JAVA_INT),
                                       (int)cfEncoding);
  }

  public static void cfRetain(ID id) {
    FoundationNative.call("CFRetain", FunctionDescriptor.of(ADDRESS, ADDRESS), id);
  }

  public static void cfRelease(ID... ids) {
    for (ID id : ids) {
      if (!isNil(id)) {
        FoundationNative.call("CFRelease", FunctionDescriptor.ofVoid(ADDRESS), id);
      }
    }
  }

  public static ID autorelease(ID id) {
    return invoke(id, "autorelease");
  }

  public static boolean isMainThread() {
    return invoke("NSThread", "isMainThread").booleanValue();
  }

  private static MethodHandle ourRunnableCallback;
  private static final String RUNNABLE_CLASS_NAME = "IdeaRunnable_" + UUID.randomUUID().toString().replace("-", "");
  private static final Map<String, RunnableInfo> ourMainThreadRunnables = new HashMap<>();
  private static long ourCurrentRunnableCount = 0;
  private static final Object RUNNABLE_LOCK = new Object();

  static final class RunnableInfo {
    RunnableInfo(Runnable runnable, boolean useAutoreleasePool) {
      myRunnable = runnable;
      myUseAutoreleasePool = useAutoreleasePool;
    }

    Runnable myRunnable;
    boolean myUseAutoreleasePool;
  }

  public static void executeOnMainThread(final boolean withAutoreleasePool, final boolean waitUntilDone, final Runnable runnable) {
    String runnableCountString;
    synchronized (RUNNABLE_LOCK) {
      initRunnableSupport();

      runnableCountString = String.valueOf(++ourCurrentRunnableCount);
      ourMainThreadRunnables.put(runnableCountString, new RunnableInfo(runnable, withAutoreleasePool));
    }

    NSAutoreleasePool pool = null;
    ID runnableObject = ID.NIL;
    ID keyObject = ID.NIL;
    var scheduled = false;
    try {
      pool = new NSAutoreleasePool();
      runnableObject = invoke(getObjcClass(RUNNABLE_CLASS_NAME), "new");
      if (isNil(runnableObject)) throw new IllegalStateException("Cannot create the main thread runnable");
      keyObject = invoke(nsString(runnableCountString), "retain");
      invoke(runnableObject, "performSelectorOnMainThread:withObject:waitUntilDone:", createSelector("run:"), keyObject, waitUntilDone);
      scheduled = true;
    }
    finally {
      try {
        if (!scheduled) {
          RunnableInfo pending;
          synchronized (RUNNABLE_LOCK) {
            pending = ourMainThreadRunnables.remove(runnableCountString);
          }
          if (pending != null) invoke(keyObject, "release");
        }
        invoke(runnableObject, "release");
      }
      finally {
        if (pool != null) pool.drain();
      }
    }
  }

  /**
   * Registers idea runnable adapter class in ObjC runtime, if not registered yet.
   * <p>
   * Warning: NOT THREAD-SAFE! Must be called under lock. Danger of segmentation fault.
   */
  private static void initRunnableSupport() {
    if (ourRunnableCallback == null) {
      final ID runnableClass = allocateObjcClassPair(getObjcClass("NSObject"), RUNNABLE_CLASS_NAME);
      var runnableCallback = new Object() {
        @SuppressWarnings("unused")
        public void callback(ID self, Selector selector, ID keyObject) {
          final String key = toStringViaUTF8(keyObject);
          invoke(keyObject, "release");

          RunnableInfo info;
          synchronized (RUNNABLE_LOCK) {
            info = ourMainThreadRunnables.remove(key);
          }

          if (info == null) {
            return;
          }

          ID pool = null;
          try {
            if (info.myUseAutoreleasePool) {
              pool = invoke("NSAutoreleasePool", "new");
            }

            info.myRunnable.run();
          }
          finally {
            if (pool != null) {
              invoke(pool, "release");
            }
          }
        }
      };
      var callback = callback(runnableCallback, "callback", ID.class, Selector.class, ID.class);
      if (!addMethod(runnableClass, createSelector("run:"), callback, "v@:@")) {
        throw new RuntimeException("Unable to add method to objective-c runnableClass class!");
      }
      registerObjcClassPair(runnableClass);
      ourRunnableCallback = callback;
    }
  }

  public static final class NSDictionary {
    private final ID myDelegate;

    public NSDictionary(ID delegate) {
      myDelegate = delegate;
    }

    public ID get(ID key) {
      return invoke(myDelegate, "objectForKey:", key);
    }

    public ID get(String key) {
      return get(nsString(key));
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }

    public NSArray keys() { return new NSArray(invoke(myDelegate, "allKeys")); }

    public static @NotNull Map<String, String> toStringMap(@Nullable ID delegate) {
      Map<String, String> result = new HashMap<>();
      if (isNil(delegate)) {
        return result;
      }

      NSDictionary dict = new NSDictionary(delegate);
      NSArray keys = dict.keys();

      for (int i = 0; i < keys.count(); i++) {
        String key = toStringViaUTF8(keys.at(i));
        String val = toStringViaUTF8(dict.get(key));
        result.put(key, val);
      }

      return result;
    }

    public static ID toStringDictionary(@NotNull Map<String, String> map) {
      ID dict = invoke("NSMutableDictionary", "dictionaryWithCapacity:", map.size());
      for (Map.Entry<String, String> entry : map.entrySet()) {
        invoke(dict, "setObject:forKey:", nsString(entry.getValue()), nsString(entry.getKey()));
      }
      return dict;
    }
  }

  public static final class NSArray {
    private final ID myDelegate;

    public NSArray(ID delegate) {
      myDelegate = delegate;
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }

    public ID at(int index) {
      return invoke(myDelegate, "objectAtIndex:", index);
    }

    public @NotNull List<ID> getList() {
      List<ID> result = new ArrayList<>();
      for (int i = 0; i < count(); i++) {
        result.add(at(i));
      }
      return result;
    }
  }

  public static final class NSData {
    private final ID myDelegate;

    // delegate should not be nil
    public NSData(@NotNull ID delegate) {
      myDelegate = delegate;
    }

    public int length() {
      return Math.toIntExact(invoke(myDelegate, "length").longValue());
    }

    public byte @NotNull [] bytes() {
      int length = length();
      return length == 0 ? new byte[0] : invoke(myDelegate, "bytes").asMemorySegment().reinterpret(length).toArray(JAVA_BYTE);
    }

    public @NotNull Image createImageFromBytes() {
      return ImageLoader.loadFromBytes(bytes());
    }
  }

  public static final class NSAutoreleasePool {
    private final ID myDelegate;

    public NSAutoreleasePool() {
      myDelegate = invoke(invoke("NSAutoreleasePool", "alloc"), "init");
    }

    public void drain() {
      invoke(myDelegate, "drain");
    }
  }

  public static final class NSRect {
    public NSPoint origin;
    public NSSize size;

    public NSRect(double x, double y, double w, double h) {
      origin = new NSPoint(x, y);
      size = new NSSize(w, h);
    }
  }

  public static final class NSPoint {
    public CoreGraphics.CGFloat x;
    public CoreGraphics.CGFloat y;

    @SuppressWarnings("UnusedDeclaration")
    public NSPoint() {
      this(0, 0);
    }

    public NSPoint(double x, double y) {
      this.x = new CoreGraphics.CGFloat(x);
      this.y = new CoreGraphics.CGFloat(y);
    }
  }

  public static final class NSSize {
    public CoreGraphics.CGFloat width;
    public CoreGraphics.CGFloat height;

    @SuppressWarnings("UnusedDeclaration")
    public NSSize() {
      this(0, 0);
    }

    public NSSize(double width, double height) {
      this.width = new CoreGraphics.CGFloat(width);
      this.height = new CoreGraphics.CGFloat(height);
    }
  }

  public static ID fillArray(final Object[] a) {
    final ID result = invoke("NSMutableArray", "array");
    for (Object s : a) {
      invoke(result, "addObject:", convertType(s));
    }

    return result;
  }

  public static ID createDict(final String @NotNull [] keys, final Object @NotNull [] values) {
    if (keys.length != values.length) throw new IllegalArgumentException("Dictionary keys and values must have the same length");
    final ID nsKeys = createArray(keys);
    final ID nsData = createArray(values);
    return invoke("NSDictionary", "dictionaryWithObjects:forKeys:", nsData, nsKeys);
  }

  public static ID createArray(Object... values) {
    try (var arena = Arena.ofConfined()) {
      var buffer = arena.allocate(ADDRESS, values.length);
      for (int index = 0; index < values.length; index++) {
        var value = convertType(values[index]);
        var pointer = value instanceof ID id ? id.asMemorySegment() : (MemorySegment)value;
        if (pointer.address() == 0) throw new IllegalArgumentException("An NSArray cannot contain nil");
        if (pointer.address() == 0) throw new IllegalArgumentException("An NSArray cannot contain nil");
        buffer.setAtIndex(ADDRESS, index, pointer);
      }
      return invoke("NSArray", "arrayWithObjects:count:", buffer, (long)values.length);
    }
  }

  public static @NotNull MemorySegment createPointerReference(@NotNull Arena arena) {
    return arena.allocate(ADDRESS);
  }

  public static @NotNull ID castPointerToNSError(@NotNull MemorySegment pointer) {
    return new ID(pointer.get(ADDRESS, 0).address());
  }

  public static Object[] convertTypes(Object @NotNull [] v) {
    final Object[] result = new Object[v.length + 1];
    for (int i = 0; i < v.length; i++) {
      result[i] = convertType(v[i]);
    }
    result[v.length] = ID.NIL;
    return result;
  }

  private static Object convertType(@NotNull Object o) {
    if (o instanceof MemorySegment || o instanceof ID) {
      return o;
    }
    else if (o instanceof String) {
      return nsString((String)o);
    }
    else {
      throw new IllegalArgumentException("Unsupported type! " + o.getClass());
    }
  }
}

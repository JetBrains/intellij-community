package org.jetbrains.plugins.javaFX.fxml;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.beans.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This is not a test, this is a config generator for JavaFxRedundantPropertyValueInspection
 * <p>
 * When launched without arguments it produces default values for JavaFX classes having default constructor and their superclasses, including some (but not all) abstract classes
 * <p>
 * When launched with <code>-fromSource</code> argument it attempts to extract default property values from the sources (JavaDoc and declarations),
 * the results can be used for updating the contents of {@link #ourFromSource} map, which contains manually edited properties
 *
 * @author Pavel.Dolgov
 */
public class JavaFxGenerateDefaultPropertyValuesScript extends Application {
  public static final String BINARIES_PATH = "/usr/lib/jvm/java-8-oracle/jre/lib/ext/jfxrt.jar";
  public static final String SOURCE_PATH = "/usr/lib/jvm/java-8-oracle/javafx-src.zip";

  public static void main(String[] args) {
    if (args.length == 1 && "-fromSource".equals(args[0])) {
      scanSource();
    }
    else {
      launch(args);
    }
  }

  @Override
  public void start(Stage primaryStage) throws Exception {
    Button b = new Button();
    b.setText("Generate");
    b.setOnAction(event -> generate());

    StackPane pane = new StackPane();
    pane.getChildren().add(b);
    primaryStage.setScene(new Scene(pane, 300, 200));
    primaryStage.show();

    Platform.runLater(() -> {
      generate();
      primaryStage.close();
    });
  }

  private static final Map<Class, Class> ourToBoxed = new HashMap<>();

  static {
    ourToBoxed.put(Boolean.TYPE, Boolean.class);
    ourToBoxed.put(Character.TYPE, Character.class);
    ourToBoxed.put(Byte.TYPE, Byte.class);
    ourToBoxed.put(Short.TYPE, Short.class);
    ourToBoxed.put(Integer.TYPE, Integer.class);
    ourToBoxed.put(Long.TYPE, Long.class);
    ourToBoxed.put(Float.TYPE, Float.class);
    ourToBoxed.put(Double.TYPE, Double.class);
  }

  private static final Map<String, String> ourFromSource = new TreeMap<>();

  static {
    ourFromSource.put("javafx.concurrent.ScheduledService#maximumFailureCount", "Integer=2147483647");
    ourFromSource.put("javafx.concurrent.ScheduledService#restartOnFailure", "Boolean=true");
    ourFromSource.put("javafx.scene.Node#accessibleRole", "Enum=NODE");
    ourFromSource.put("javafx.scene.Node#focusTraversable", "Boolean=false");
    ourFromSource.put("javafx.scene.Node#nodeOrientation", "Enum=INHERIT");
    ourFromSource.put("javafx.scene.Node#pickOnBounds", "Boolean=false");
    ourFromSource.put("javafx.scene.SubScene#height", "Double=0.0");
    ourFromSource.put("javafx.scene.SubScene#width", "Double=0.0");
    ourFromSource.put("javafx.scene.chart.AreaChart#createSymbols", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.Axis#label", "String=");
    ourFromSource.put("javafx.scene.chart.BarChart#barGap", "Double=4.0");
    ourFromSource.put("javafx.scene.chart.BarChart#categoryGap", "Double=10.0");
    ourFromSource.put("javafx.scene.chart.Chart#title", "String=");
    ourFromSource.put("javafx.scene.chart.LineChart#axisSortingPolicy", "Enum=X_AXIS");
    ourFromSource.put("javafx.scene.chart.LineChart#createSymbols", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.StackedAreaChart#createSymbols", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.StackedBarChart#categoryGap", "Double=10.0");
    ourFromSource.put("javafx.scene.chart.XYChart#alternativeColumnFillVisible", "Boolean=false");
    ourFromSource.put("javafx.scene.chart.XYChart#alternativeRowFillVisible", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.XYChart#horizontalGridLinesVisible", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.XYChart#horizontalZeroLineVisible", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.XYChart#verticalGridLinesVisible", "Boolean=true");
    ourFromSource.put("javafx.scene.chart.XYChart#verticalZeroLineVisible", "Boolean=true");
    ourFromSource.put("javafx.scene.control.ComboBoxBase#editable", "Boolean=false");
    ourFromSource.put("javafx.scene.control.CustomMenuItem#hideOnClick", "Boolean=true");
    ourFromSource.put("javafx.scene.control.Labeled#alignment", "Enum=CENTER_LEFT");
    ourFromSource.put("javafx.scene.control.Labeled#mnemonicParsing", "Boolean=false");
    ourFromSource.put("javafx.scene.control.SpinnerValueFactory#wrapAround", "Boolean=false");
    ourFromSource.put("javafx.scene.control.TableSelectionModel#cellSelectionEnabled", "Boolean=false");
    ourFromSource.put("javafx.scene.media.AudioClip#balance", "Double=0.0");
    ourFromSource.put("javafx.scene.media.AudioClip#cycleCount", "Integer=1");
    ourFromSource.put("javafx.scene.media.AudioClip#pan", "Double=0.0");
    ourFromSource.put("javafx.scene.media.AudioClip#priority", "Integer=0");
    ourFromSource.put("javafx.scene.media.AudioClip#rate", "Double=1.0");
    ourFromSource.put("javafx.scene.media.AudioClip#volume", "Double=1.0");
    ourFromSource.put("javafx.scene.media.AudioEqualizer#enabled", "Boolean=false");
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumInterval", "Double=0.1");
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumNumBands", "Integer=128");
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumThreshold", "Integer=-60");
    ourFromSource.put("javafx.scene.media.MediaPlayer#autoPlay", "Boolean=false");
    ourFromSource.put("javafx.scene.media.MediaPlayer#balance", "Double=0.0");
    ourFromSource.put("javafx.scene.media.MediaPlayer#cycleCount", "Integer=1");
    ourFromSource.put("javafx.scene.media.MediaPlayer#mute", "Boolean=false");
    ourFromSource.put("javafx.scene.media.MediaPlayer#rate", "Double=1.0");
    ourFromSource.put("javafx.scene.media.MediaPlayer#volume", "Double=1.0");
    ourFromSource.put("javafx.stage.PopupWindow#anchorLocation", "Enum=WINDOW_TOP_LEFT");
    ourFromSource.put("javafx.stage.PopupWindow#autoHide", "Boolean=false");
    ourFromSource.put("javafx.stage.PopupWindow#consumeAutoHidingEvents", "Boolean=true");
  }

  private static final Set<String> ourSkippedProperties = new HashSet<>(
    Arrays.asList("javafx.scene.web.HTMLEditor#htmlText",
                  "javafx.scene.web.WebEngine#userAgent",
                  "javafx.scene.control.ButtonBar#buttonOrder"));

  static class TypedValue {

    private final String kind;
    private final String value;

    TypedValue(@NotNull String kind, @NotNull String value) {
      this.kind = kind;
      this.value = value;
    }

    public String getKind() {
      return kind;
    }

    public String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TypedValue)) return false;

      TypedValue that = (TypedValue)o;
      return kind.equals(that.kind) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return kind.hashCode() ^ value.hashCode();
    }

    @Override
    public String toString() {
      return kind + ':' + value;
    }
  }

  static class ContainedValue extends TypedValue {
    private final String declaringClass;

    public ContainedValue(@NotNull String kind, @NotNull String value, @NotNull String declaringClass) {
      super(kind, value);
      this.declaringClass = declaringClass;
    }

    public String getDeclaringClass() {
      return declaringClass;
    }
  }

  /**
   * Attempt to instantiate JavaFX classes on the FX thread, obtain default property values from instantiated objects.
   */
  private static void generate() {
    System.out.println("--- JavaFX default property values ---");

    final Map<String, Map<String, ContainedValue>> containedProperties = new TreeMap<>();
    final Map<String, Map<String, TypedValue>> declaredProperties = new TreeMap<>();
    final Map<String, Map<String, Set<String>>> overriddenProperties = new TreeMap<>();
    final Map<String, String> superClasses = new TreeMap<>();
    try (final ZipInputStream zip = new ZipInputStream(new FileInputStream(new File(BINARIES_PATH)))) {
      for (ZipEntry ze = zip.getNextEntry(); ze != null; ze = zip.getNextEntry()) {
        final String entryName = ze.getName();
        if (!ze.isDirectory() && entryName.endsWith(".class") && entryName.startsWith("javafx")) {
          final String className = entryName.substring(0, entryName.lastIndexOf('.')).replace('/', '.');
          Class<?> currentClass = Class.forName(className);
          if (!Modifier.isPublic(currentClass.getModifiers()) && !Modifier.isProtected(currentClass.getModifiers())) continue;
          if (currentClass.getName().startsWith("javafx.beans")) continue;
          if (currentClass.getName().matches(".*\\$\\d+$")) continue;
          BeanInfo info = Introspector.getBeanInfo(currentClass);
          if (currentClass.getSuperclass() != null) {
            superClasses.put(currentClass.getName(), currentClass.getSuperclass().getName());
          }

          Set<String> bindings = new TreeSet<>();
          for (MethodDescriptor methodDesc : info.getMethodDescriptors()) {
            if (methodDesc.getName().endsWith("Property") &&
                !methodDesc.getMethod().getGenericReturnType().toString().contains("ReadOnly")) {
              bindings.add(methodDesc.getName());
            }
          }

          Object obj = null;
          for (PropertyDescriptor desc : info.getPropertyDescriptors()) {
            final String propName = desc.getName();
            final String propQualifiedName = currentClass.getName() + "#" + propName;
            if (ourSkippedProperties.contains(propQualifiedName)) continue;
            final Method setter = desc.getWriteMethod();
            final boolean hasBinding = bindings.contains(propName + "Property");
            if (setter == null || !hasBinding) continue;
            final Type type = setter.getGenericParameterTypes()[0];

            if (type instanceof Class) {
              final Class<?> paramCls = (Class)type;
              final String kind = kind(paramCls);
              if (kind != null) {
                if (obj == null) {
                  obj = instantiate(currentClass);
                  if (obj == null) break;
                }
                final Object value;
                final Method getter = desc.getReadMethod();
                try {
                  value = getter.invoke(obj);
                }
                catch (IllegalAccessException | InvocationTargetException e) {
                  throw new RuntimeException("Can't invoke " + getter + " on " + currentClass, e);
                }
                if (value != null) {
                  if (!value.equals(validate(paramCls, value))) {
                    throw new RuntimeException("Invalid " + currentClass + "#" + propName + ":" + paramCls + "=" + value);
                  }
                  final Class<?> declaringClass = getter.getDeclaringClass();
                  final ContainedValue newValue = new ContainedValue(kind, String.valueOf(value), declaringClass.getName());
                  if (declaringClass.getName().startsWith("javafx")) {
                    containedProperties
                      .computeIfAbsent(currentClass.getName(), unused -> new TreeMap<>())
                      .put(propName, newValue);
                  }

                  final Map<String, TypedValue> shareableProperties =
                    declaredProperties.computeIfAbsent(declaringClass.getName(), unused -> new TreeMap<>());
                  final TypedValue sharedValue = shareableProperties.get(propName);

                  if (sharedValue == null) {
                    shareableProperties.put(propName, newValue);
                  }
                  else if (!sharedValue.equals(newValue)) {
                    final Set<String> multipleValues = overriddenProperties
                      .computeIfAbsent(declaringClass.getName(), unused -> new TreeMap<>())
                      .computeIfAbsent(propName, unused -> new TreeSet<>());
                    multipleValues.add(sharedValue.getValue());
                    multipleValues.add(newValue.getValue());
                  }
                }
              }
            }
          }
        }
      }
    }
    catch (IOException | ClassNotFoundException | IntrospectionException e) {
      e.printStackTrace();
    }

    System.out.println("-------- Collected from sources ---------");
    ourFromSource.forEach((k, v) -> System.out.println(k + ":" + v));

    System.out.println("-------- Shared (not overridden) ---------");
    declaredProperties.forEach(
      (className, propertyMap) -> {
        final Map<String, Set<String>> multipleValues = overriddenProperties.getOrDefault(className, Collections.emptyMap());
        propertyMap.forEach((propName, typedValue) -> {
          if (!multipleValues.containsKey(propName)) {
            System.out.println(className + "#" + propName + ":" + typedValue.getKind() + "=" + typedValue.getValue());
          }
        });
      });
    System.out.println("-------- Overridden in subclass ---------");
    final Map<String, Map<String, ContainedValue>> fromSource = new TreeMap<>();
    ourFromSource.forEach((classAndPropName, kindAndValue) -> {
      final int p1 = classAndPropName.indexOf('#');
      if (p1 > 0 && p1 < classAndPropName.length()) {
        final String className = classAndPropName.substring(0, p1);
        final String propName = classAndPropName.substring(p1 + 1);
        final int p3 = kindAndValue.indexOf('=');
        if (p3 > 0 && p3 < kindAndValue.length()) {
          final String kind = kindAndValue.substring(0, p3);
          final String value = kindAndValue.substring(p3 + 1);
          final Map<String, ContainedValue> propMap = fromSource.computeIfAbsent(className, unused -> new TreeMap<>());
          if (!propMap.containsKey(propName)) {
            final ContainedValue valueFromSource = new ContainedValue(kind, value, className);
            propMap.put(propName, valueFromSource);
          }
        }
      }
    });
    containedProperties.forEach(
      (className, propertyMap) -> propertyMap.forEach(
        (propName, propValue) -> {
          final ContainedValue sourceValue = fromSource.getOrDefault(className, Collections.emptyMap()).get(propName);
          if (sourceValue != null &&
              sourceValue.equals(propValue)) {
            return;
          }
          final Map<String, Set<String>> multipleValues = overriddenProperties.get(propValue.getDeclaringClass());
          if (multipleValues != null && multipleValues.get(propName) != null) {
            boolean sameValueInSuperClass = false;
            for (String scName = superClasses.get(className); scName != null; scName = superClasses.get(scName)) {
              final Map<String, ContainedValue> superPropMap = containedProperties.getOrDefault(scName, fromSource.get(scName));
              if (superPropMap != null) {
                ContainedValue superValue = superPropMap.get(propName);
                if (superValue != null) {
                  sameValueInSuperClass = superValue.equals(propValue);
                  break;
                }
              }
            }
            if (!sameValueInSuperClass) {
              System.out.println(className + "#" + propName + ":" + propValue.getKind() + "=" + propValue.getValue());
            }
          }
        }));

    System.out.println("-------- Overridden properties' values ---------");
    overriddenProperties
      .forEach((className, propertyValues) -> propertyValues.entrySet()
        .forEach(e -> System.out.println("-- " + className + "#" + e.getKey() + e.getValue())));
    System.out.println("-------- Skipped properties ---------");
    ourSkippedProperties.forEach(propName -> System.out.println("-- " + propName));
  }

  private static String kind(Class aCls) {
    String kind = null;
    if (aCls.isPrimitive()) {
      kind = ourToBoxed.get(aCls).getSimpleName();
    }
    else if (aCls.isEnum()) {
      kind = "Enum";
    }
    else if (Number.class.isAssignableFrom(aCls) || Boolean.class.isAssignableFrom(aCls) || Character.class.isAssignableFrom(aCls)) {
      kind = aCls.getSimpleName();
    }
    else if (CharSequence.class.isAssignableFrom(aCls)) {
      kind = "String";
    }
    return kind;
  }

  private static Object validate(Class<?> aCls, Object val) {
    try {
      if (aCls.isPrimitive()) {
        aCls = ourToBoxed.get(aCls);
        Method valueOf = aCls.getDeclaredMethod("valueOf", String.class);
        return valueOf.invoke(null, String.valueOf(val));
      }
      if (Number.class.isAssignableFrom(aCls) || Boolean.class.isAssignableFrom(aCls) || Character.class.isAssignableFrom(aCls)) {
        Method valueOf = aCls.getDeclaredMethod("valueOf", String.class);
        return valueOf.invoke(null, String.valueOf(val));
      }
      if (CharSequence.class.isAssignableFrom(aCls)) {
        return val;
      }
      if (aCls.isEnum()) {
        Method valueOf = aCls.getDeclaredMethod("valueOf", String.class);
        return valueOf.invoke(null, String.valueOf(val));
      }
      throw new IllegalStateException("Cannot cast " + val + " to unsupported class " + aCls);
    }
    catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("Cannot cast " + val + " to " + aCls, e);
    }
  }

  private static Object instantiate(Class<?> aClass) {
    try {
      if (Modifier.isAbstract(aClass.getModifiers())) return null;

      final Constructor<?> constructor;
      try {
        constructor = aClass.getConstructor();
      }
      catch (NoSuchMethodException e) {
        return null;
      }
      constructor.setAccessible(true);
      return constructor.newInstance();
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      System.err.println("Not instantiated " + aClass + " " + e);
      return null;
    }
  }

  private static final Set<String> ourSourceClasses = new TreeSet<>();

  static {
    ourSourceClasses.add("javafx.animation.Animation");
    ourSourceClasses.add("javafx.concurrent.ScheduledService");
    ourSourceClasses.add("javafx.print.JobSettings");
    ourSourceClasses.add("javafx.scene.Camera");
    ourSourceClasses.add("javafx.scene.LightBase");
    ourSourceClasses.add("javafx.scene.Node");
    ourSourceClasses.add("javafx.scene.Scene");
    ourSourceClasses.add("javafx.scene.SubScene");
    ourSourceClasses.add("javafx.scene.canvas.GraphicsContext");
    ourSourceClasses.add("javafx.scene.chart.AreaChart");
    ourSourceClasses.add("javafx.scene.chart.Axis");
    ourSourceClasses.add("javafx.scene.chart.BarChart");
    ourSourceClasses.add("javafx.scene.chart.Chart");
    ourSourceClasses.add("javafx.scene.chart.LineChart");
    ourSourceClasses.add("javafx.scene.chart.PieChart");
    ourSourceClasses.add("javafx.scene.chart.StackedAreaChart");
    ourSourceClasses.add("javafx.scene.chart.StackedBarChart");
    ourSourceClasses.add("javafx.scene.chart.ValueAxis");
    ourSourceClasses.add("javafx.scene.chart.XYChart");
    ourSourceClasses.add("javafx.scene.control.Alert");
    ourSourceClasses.add("javafx.scene.control.ComboBoxBase");
    ourSourceClasses.add("javafx.scene.control.Labeled");
    ourSourceClasses.add("javafx.scene.control.MultipleSelectionModel");
    ourSourceClasses.add("javafx.scene.control.SpinnerValueFactory");
    ourSourceClasses.add("javafx.scene.control.SpinnerValueFactory");
    ourSourceClasses.add("javafx.scene.control.SpinnerValueFactory");
    ourSourceClasses.add("javafx.scene.control.TableColumnBase");
    ourSourceClasses.add("javafx.scene.control.TableSelectionModel");
    ourSourceClasses.add("javafx.scene.control.TextFormatter");
    ourSourceClasses.add("javafx.scene.control.TextInputControl");
    ourSourceClasses.add("javafx.scene.control.Toggle");
    ourSourceClasses.add("javafx.scene.input.DragEvent");
    ourSourceClasses.add("javafx.scene.input.Dragboard");
    ourSourceClasses.add("javafx.scene.input.MouseEvent");
    ourSourceClasses.add("javafx.scene.media.AudioClip");
    ourSourceClasses.add("javafx.scene.media.AudioEqualizer");
    ourSourceClasses.add("javafx.scene.media.MediaPlayer");
    ourSourceClasses.add("javafx.scene.shape.PathElement");
    ourSourceClasses.add("javafx.scene.shape.Shape");
    ourSourceClasses.add("javafx.scene.shape.Shape3D");
    ourSourceClasses.add("javafx.scene.web.WebHistory");
    ourSourceClasses.add("javafx.stage.PopupWindow");
    ourSourceClasses.add("javafx.stage.Window");
  }

  private static void scanSource() {

    Pattern defaultValueJavaDoc = Pattern.compile("^.*\\*\\s*@defaultValue\\s*(.+)\\s*$");
    Pattern fieldDecl = Pattern.compile("^.*\\s(\\w+)\\s*(=.*|;)\\s*$");
    Pattern methodDecl = Pattern.compile("^.*\\s(\\w+)\\s*\\(\\).*$");
    Pattern propertyDecl = Pattern.compile("^.*Property\\S*\\s+(\\w+)\\s*=\\s*(.+)[;{].*$");

    Map<String, String> props = new TreeMap<>();
    try (final ZipInputStream zip = new ZipInputStream(new FileInputStream(new File(SOURCE_PATH)))) {
      byte[] buffer = new byte[1 << 16];
      for (ZipEntry ze = zip.getNextEntry(); ze != null; ze = zip.getNextEntry()) {
        final String eName = ze.getName();
        if (!ze.isDirectory() && eName.endsWith(".java") && eName.startsWith("javafx")) {
          String className = eName.substring(0, eName.lastIndexOf('.')).replace('/', '.');
          if (ourSourceClasses.contains(className)) {
            StringBuilder text = new StringBuilder();
            int len;
            while ((len = zip.read(buffer)) > 0) {
              String str = new String(buffer, 0, len);
              text.append(str);
            }
            String[] lines = text.toString().split("\n");
            int state = 0;
            String name = null;
            String value = null;
            for (String s : lines) {
              Matcher m;
              m = propertyDecl.matcher(s);
              if (m.matches()) {
                name = m.group(1);
                value = m.group(2);
                if (!"null".equals(value)) {
                  props.put(className + "#" + name, value);
                }
              }
              switch (state) {
                case 0:
                  m = defaultValueJavaDoc.matcher(s);
                  if (m.matches()) {
                    state = 1;
                    value = m.group(1);
                  }
                  break;
                case 1:
                  if (s.contains("*/")) state = 2;
                  break;
                case 2:
                  if (!s.trim().isEmpty()) {
                    state = 0;
                    m = fieldDecl.matcher(s);
                    if (m.matches()) {
                      name = m.group(1);
                    }
                    else {
                      m = methodDecl.matcher(s);
                      if (m.matches()) {
                        name = m.group(1);
                      }
                    }
                    if (name != null && value != null && !"null".equals(value)) {
                      props.put(className + "#" + name, value);
                    }
                    name = value = null;
                  }
                  break;
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      System.err.println("Failed to read sources " + e);
    }
    System.out.println("--- Default values collected from JavaDoc and declarations. To be reviewed and manually edited ---");
    props.forEach((n, v) -> System.out.println(n + "=" + v));
  }
}

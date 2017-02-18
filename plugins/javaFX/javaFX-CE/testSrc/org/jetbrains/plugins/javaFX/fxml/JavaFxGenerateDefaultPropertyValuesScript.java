package org.jetbrains.plugins.javaFX.fxml;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.beans.value.WritableValue;
import javafx.event.Event;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.PopupWindow;
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
 * This is not a test, this is a resource generator for JavaFxRedundantPropertyValueInspection
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

  private static final Map<String, Object> ourFromSource = new TreeMap<>();

  static {
    ourFromSource.put("javafx.concurrent.ScheduledService#maximumFailureCount", Integer.MAX_VALUE);
    ourFromSource.put("javafx.concurrent.ScheduledService#restartOnFailure", true);
    ourFromSource.put("javafx.scene.Node#accessibleRole", AccessibleRole.NODE);
    ourFromSource.put("javafx.scene.Node#focusTraversable", false);
    ourFromSource.put("javafx.scene.Node#nodeOrientation", NodeOrientation.INHERIT);
    ourFromSource.put("javafx.scene.Node#pickOnBounds", false);
    ourFromSource.put("javafx.scene.SubScene#height", 0.0);
    ourFromSource.put("javafx.scene.SubScene#width", 0.0);
    ourFromSource.put("javafx.scene.chart.AreaChart#createSymbols", true);
    ourFromSource.put("javafx.scene.chart.Axis#label", "");
    ourFromSource.put("javafx.scene.chart.BarChart#barGap", 4.0);
    ourFromSource.put("javafx.scene.chart.BarChart#categoryGap", 10.0);
    ourFromSource.put("javafx.scene.chart.Chart#title", "");
    ourFromSource.put("javafx.scene.chart.LineChart#axisSortingPolicy", LineChart.SortingPolicy.X_AXIS);
    ourFromSource.put("javafx.scene.chart.LineChart#createSymbols", true);
    ourFromSource.put("javafx.scene.chart.StackedAreaChart#createSymbols", true);
    ourFromSource.put("javafx.scene.chart.StackedBarChart#categoryGap", 10.0);
    ourFromSource.put("javafx.scene.chart.XYChart#alternativeColumnFillVisible", false);
    ourFromSource.put("javafx.scene.chart.XYChart#alternativeRowFillVisible", true);
    ourFromSource.put("javafx.scene.chart.XYChart#horizontalGridLinesVisible", true);
    ourFromSource.put("javafx.scene.chart.XYChart#horizontalZeroLineVisible", true);
    ourFromSource.put("javafx.scene.chart.XYChart#verticalGridLinesVisible", true);
    ourFromSource.put("javafx.scene.chart.XYChart#verticalZeroLineVisible", true);
    ourFromSource.put("javafx.scene.control.ComboBoxBase#editable", false);
    ourFromSource.put("javafx.scene.control.CustomMenuItem#hideOnClick", true);
    ourFromSource.put("javafx.scene.control.Labeled#alignment", Pos.CENTER_LEFT);
    ourFromSource.put("javafx.scene.control.Labeled#mnemonicParsing", false);
    ourFromSource.put("javafx.scene.control.SpinnerValueFactory#wrapAround", false);
    ourFromSource.put("javafx.scene.control.TableSelectionModel#cellSelectionEnabled", false);
    ourFromSource.put("javafx.scene.media.AudioClip#balance", 0.0);
    ourFromSource.put("javafx.scene.media.AudioClip#cycleCount", 1);
    ourFromSource.put("javafx.scene.media.AudioClip#pan", 0.0);
    ourFromSource.put("javafx.scene.media.AudioClip#priority", 0);
    ourFromSource.put("javafx.scene.media.AudioClip#rate", 1.0);
    ourFromSource.put("javafx.scene.media.AudioClip#volume", 1.0);
    ourFromSource.put("javafx.scene.media.AudioEqualizer#enabled", false);
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumInterval", 0.1);
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumNumBands", 128);
    ourFromSource.put("javafx.scene.media.MediaPlayer#audioSpectrumThreshold", -60);
    ourFromSource.put("javafx.scene.media.MediaPlayer#autoPlay", false);
    ourFromSource.put("javafx.scene.media.MediaPlayer#balance", 0.0);
    ourFromSource.put("javafx.scene.media.MediaPlayer#cycleCount", 1);
    ourFromSource.put("javafx.scene.media.MediaPlayer#mute", false);
    ourFromSource.put("javafx.scene.media.MediaPlayer#rate", 1.0);
    ourFromSource.put("javafx.scene.media.MediaPlayer#volume", 1.0);
    ourFromSource.put("javafx.stage.PopupWindow#anchorLocation", PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
    ourFromSource.put("javafx.stage.PopupWindow#autoHide", false);
    ourFromSource.put("javafx.stage.PopupWindow#consumeAutoHidingEvents", true);
  }

  private static final Set<String> ourSkippedProperties = new HashSet<>(
    Arrays.asList("javafx.scene.web.HTMLEditor#htmlText",
                  "javafx.scene.web.WebEngine#userAgent",
                  "javafx.scene.control.ButtonBar#buttonOrder"));


  /**
   * Attempt to instantiate JavaFX classes on the FX thread, obtain default property values from instantiated objects.
   */
  private static void generate() {
    System.out.println("--- JavaFX default property values ---");

    final Map<String, Map<String, DefaultValue>> defaultPropertyValues = new TreeMap<>();
    final Map<String, Map<String, String>> declaredProperties = new TreeMap<>();
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
          Map<String, Object> constructorNamedArgValues = constructorNamedArgValues(currentClass);

          Object instance = null;
          for (PropertyDescriptor desc : info.getPropertyDescriptors()) {
            final String propName = desc.getName();
            final String propQualifiedName = currentClass.getName() + "#" + propName;
            if (ourSkippedProperties.contains(propQualifiedName)) continue;
            final Method setter = desc.getWriteMethod();
            final boolean hasBinding = bindings.contains(propName + "Property");

            final Object value;
            final Class<?> declaringClass;
            final Type type;
            if (setter != null &&
                hasBinding &&
                (type = setter.getGenericParameterTypes()[0]) instanceof Class &&
                isSupportedPropertyType((Class)type)) {
              if (instance == null) {
                instance = instantiate(currentClass);
                if (instance == null) break;
              }
              final Method getter = desc.getReadMethod();
              try {
                value = getter.invoke(instance);
              }
              catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Can't invoke " + getter + " on " + currentClass, e);
              }
              declaringClass = getter.getDeclaringClass();
            }
            else {
              value = constructorNamedArgValues.get(propName);
              if (value == null) continue;
              declaringClass = currentClass;
            }
            if (value != null) {
              final DefaultValue newValue = new DefaultValue(value, declaringClass.getName());
              if (declaringClass.getName().startsWith("javafx")) {
                defaultPropertyValues
                  .computeIfAbsent(currentClass.getName(), unused -> new TreeMap<>())
                  .put(propName, newValue);
              }

              final Map<String, String> shareableProperties =
                declaredProperties.computeIfAbsent(declaringClass.getName(), unused -> new TreeMap<>());
              final String sharedValue = shareableProperties.get(propName);

              if (sharedValue == null) {
                shareableProperties.put(propName, newValue.getValueText());
              }
              else if (!sharedValue.equals(newValue.getValueText())) {
                final Set<String> multipleValues = overriddenProperties
                  .computeIfAbsent(declaringClass.getName(), unused -> new TreeMap<>())
                  .computeIfAbsent(propName, unused -> new TreeSet<>());
                multipleValues.add(sharedValue);
                multipleValues.add(newValue.getValueText());
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
    ourFromSource.forEach((qualifiedPropName, value) -> System.out.println(qualifiedPropName + "=" + value));

    System.out.println("-------- Shared (not overridden) ---------");
    declaredProperties.forEach(
      (className, propertyMap) -> {
        final Map<String, Set<String>> multipleValues = overriddenProperties.getOrDefault(className, Collections.emptyMap());
        propertyMap.forEach((propName, valueText) -> {
          if (!multipleValues.containsKey(propName)) {
            System.out.println(className + "#" + propName + "=" + valueText);
          }
        });
      });
    System.out.println("-------- Overridden in subclass ---------");
    final Map<String, Map<String, DefaultValue>> fromSource = new TreeMap<>();
    ourFromSource.forEach((qualifiedPropName, value) -> {
      final int p = qualifiedPropName.indexOf('#');
      if (p > 0 && p < qualifiedPropName.length()) {
        final String className = qualifiedPropName.substring(0, p);
        final String propName = qualifiedPropName.substring(p + 1);
        fromSource.computeIfAbsent(className, unused -> new TreeMap<>())
          .computeIfAbsent(propName, unused -> new DefaultValue(value, className));
      }
    });
    defaultPropertyValues.forEach(
      (className, propertyMap) -> propertyMap.forEach(
        (propName, propValue) -> {
          final DefaultValue sourceValue = fromSource.getOrDefault(className, Collections.emptyMap()).get(propName);
          if (sourceValue != null && areValuesEqual(propValue, sourceValue)) {
            return;
          }
          final Map<String, Set<String>> multipleValues = overriddenProperties.get(propValue.getDeclaringClass());
          if (multipleValues != null && multipleValues.get(propName) != null) {
            boolean sameValueInSuperClass = false;
            for (String scName = superClasses.get(className); scName != null; scName = superClasses.get(scName)) {
              final Map<String, DefaultValue> superPropMap = defaultPropertyValues.getOrDefault(scName, fromSource.get(scName));
              if (superPropMap != null) {
                DefaultValue superValue = superPropMap.get(propName);
                if (superValue != null) {
                  sameValueInSuperClass = areValuesEqual(propValue, superValue);
                  break;
                }
              }
            }
            if (!sameValueInSuperClass) {
              System.out.println(className + "#" + propName + "=" + propValue.getValueText());
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

  private static class Args implements Iterable<String> {
    final Set<String> names = new TreeSet<>();
    final Map<String, Set<Type>> types = new TreeMap<>();
    final Map<String, Set<Object>> values = new TreeMap<>();

    void add(String name, Class<?> type, Object value) {
      names.add(name);
      types.computeIfAbsent(name, n -> new HashSet<>()).add(type);
      values.computeIfAbsent(name, n -> new HashSet<>()).add(value);
    }

    boolean isEmpty() {
      return names.isEmpty();
    }

    @Override
    public Iterator<String> iterator() {
      return names.iterator();
    }

    public Type getType(String name) {
      final Set<Type> typeSet = types.get(name);
      if (typeSet != null && typeSet.size() == 1) {
        return typeSet.iterator().next();
      }
      return null;
    }

    public Object getValue(String name) {
      final Set<Object> valueSet = values.get(name);
      if (valueSet != null && valueSet.size() == 1) {
        return valueSet.iterator().next();
      }
      return null;
    }
  }

  @NotNull
  private static Map<String, Object> constructorNamedArgValues(Class<?> aClass) {
    if (aClass.isInterface() ||
        aClass.isAnnotation() ||
        WritableValue.class.isAssignableFrom(aClass) ||
        Event.class.isAssignableFrom(aClass)) {
      return Collections.emptyMap();
    }
    final Constructor<?>[] constructors = aClass.getConstructors();
    final Args args = new Args();
    for (Constructor<?> constructor : constructors) {
      final Parameter[] parameters = constructor.getParameters();
      for (Parameter parameter : parameters) {
        final Class<?> type = parameter.getType();
        if (type.isPrimitive() || type.isEnum() || type == String.class) {
          final NamedArg namedArg = parameter.getAnnotation(NamedArg.class);
          if (namedArg == null) continue;
          final String name = namedArg.value();
          if (!name.isEmpty()) {
            final String defaultValue = namedArg.defaultValue();
            if ((type == String.class || type.isEnum()) && !defaultValue.isEmpty() && !"\"\"".equals(defaultValue)) {
              args.add(name, type, defaultValue);
            }
            else if (type == boolean.class) {
              args.add(name, type, Boolean.valueOf(defaultValue));
            }
            else if (type == int.class) {
              try {
                args.add(name, type, Integer.valueOf(defaultValue));
              }
              catch (NumberFormatException e) {
                args.add(name, type, Integer.valueOf(0));
              }
            }
            else if (type == long.class) {
              try {
                args.add(name, type, Long.valueOf(defaultValue));
              }
              catch (NumberFormatException e) {
                args.add(name, type, Long.valueOf(0));
              }
            }
            else if (type == double.class) {
              try {
                args.add(name, type, Double.valueOf(defaultValue));
              }
              catch (NumberFormatException e) {
                args.add(name, type, Double.valueOf(0));
              }
            }
            else if (type == float.class) {
              try {
                args.add(name, type, Float.valueOf(defaultValue));
              }
              catch (NumberFormatException e) {
                args.add(name, type, Float.valueOf(0));
              }
            }
            else if (!type.isEnum() && type != String.class) {
              System.err.println("pri " + type);
            }
          }
        }
      }
    }
    if (args.isEmpty()) return Collections.emptyMap();

    Map<String, Object> result = new TreeMap<>();
    for (String name : args) {
      final Type type = args.getType(name);
      if (type != null) {
        final Object value = args.getValue(name);
        if (value != null) {
          result.put(name, value);
        }
      }
    }
    return result;
  }


  private static boolean areValuesEqual(DefaultValue first, DefaultValue second) {
    return second.getValueText().equals(first.getValueText());
  }

  private static boolean isSupportedPropertyType(Class<?> propertyClass) {
    return propertyClass.isPrimitive() ||
           propertyClass.isEnum() ||
           Number.class.isAssignableFrom(propertyClass) ||
           Boolean.class.isAssignableFrom(propertyClass) ||
           Character.class.isAssignableFrom(propertyClass) ||
           CharSequence.class.isAssignableFrom(propertyClass);
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

  private static class DefaultValue {
    private final String myValueText;
    private final String myDeclaringClass;

    public DefaultValue(@NotNull Object value, @NotNull String declaringClass) {
      myValueText = String.valueOf(value);
      myDeclaringClass = declaringClass;
    }

    public String getDeclaringClass() {
      return myDeclaringClass;
    }

    public String getValueText() {
      return myValueText;
    }

    @Override
    public String toString() {
      return myValueText;
    }
  }
}

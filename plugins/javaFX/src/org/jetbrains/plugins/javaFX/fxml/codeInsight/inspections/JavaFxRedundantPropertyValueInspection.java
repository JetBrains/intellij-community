package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyElementDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRedundantPropertyValueInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxRedundantPropertyValueInspection.class.getName());

  private static Reference<Map<String, Map<String, String>>> ourDefaultPropertyValues;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlFile(XmlFile file) {
        if (!JavaFxFileTypeFactory.isFxml(file)) return;
        super.visitXmlFile(file);
      }

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        super.visitXmlAttribute(attribute);
        final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
        if (!(descriptor instanceof JavaFxPropertyAttributeDescriptor)) return;
        final String attributeName = attribute.getName();
        final String attributeValue = attribute.getValue();
        if (attributeValue == null ||
            attributeValue.startsWith("$") ||
            attributeValue.startsWith("#") ||
            attributeValue.startsWith("%") ||
            FxmlConstants.FX_ID.equals(attributeName) ||
            FxmlConstants.FX_CONTROLLER.equals(attributeName)) {
          return;
        }

        final Object defaultValue = getDefaultValue(descriptor, attributeName, attribute.getParent());
        if (defaultValue == null) return;

        if (isEqualValue(attributeValue, defaultValue)) {
          holder.registerProblem(attribute, "Attribute is redundant because it contains default value",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new RemoveAttributeIntentionFix(attributeName, attribute));
        }
      }

      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (!(descriptor instanceof JavaFxPropertyElementDescriptor)) {
          return;
        }
        if (tag.getSubTags().length != 0) return;
        final String tagText = tag.getValue().getText().trim();
        if (tagText.startsWith("$") ||
            tagText.startsWith("#") ||
            tagText.startsWith("%")) {
          return;
        }

        final Object defaultValue = getDefaultValue(descriptor, tag.getName(), tag.getParentTag());
        if (defaultValue == null) return;

        if (isEqualValue(tagText, defaultValue)) {
          holder.registerProblem(tag, "Tag is redundant because it contains default value",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new RemoveTagFix(tag.getName()));
        }
      }
    };
  }

  @Nullable
  private static Object getDefaultValue(PsiMetaData propertyDescriptor, @NotNull String propertyName, @Nullable XmlTag enclosingTag) {
    if (enclosingTag != null) {
      final XmlElementDescriptor descriptor = enclosingTag.getDescriptor();
      if (descriptor != null) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiClass) {
          for (PsiClass psiClass = ((PsiClass)declaration); psiClass != null; psiClass = psiClass.getSuperClass()) {
            final String qualifiedName = psiClass.getQualifiedName();
            if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) break;
            final String defaultValue = getDefaultPropertyValue(qualifiedName, propertyName);
            if (defaultValue != null) {
              return getBoxedValue(propertyDescriptor.getDeclaration(), defaultValue);
            }
          }
        }
      }
    }
    return null;
  }

  private static Object getBoxedValue(PsiElement declaration, String value) {
    String boxedQName = JavaFxPsiUtil.getBoxedPropertyType(declaration);
    if (boxedQName == null) return value;
    try {
      final Class<?> boxedClass = Class.forName(boxedQName);
      final Method method = boxedClass.getMethod(JavaFxCommonNames.VALUE_OF, String.class);
      return method.invoke(boxedClass, value);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException ignored) {
      return value;
    }
  }

  private static boolean isEqualValue(@NotNull String attributeValue, @NotNull Object defaultValue) {
    if (defaultValue instanceof String && defaultValue.equals(attributeValue)) return true;
    if (defaultValue instanceof Boolean) return defaultValue == Boolean.valueOf(attributeValue);
    if (defaultValue instanceof Double) {
      try {
        return Double.compare((Double)defaultValue, Double.parseDouble(attributeValue)) == 0;
      }
      catch (NumberFormatException ignored) {
        return false;
      }
    }
    if (defaultValue instanceof Integer) {
      try {
        return Integer.compare((Integer)defaultValue, Integer.parseInt(attributeValue)) == 0;
      }
      catch (NumberFormatException ignored) {
        return false;
      }
    }
    return false;
  }

  @Nullable
  private static String getDefaultPropertyValue(String classQualifiedName, String propertyName) {
    final Map<String, String> values = getDefaultPropertyValues(classQualifiedName);
    return values != null ? values.get(propertyName) : null;
  }

  /**
   * Load property values resource. The resource is produced with the script JavaFxGenerateDefaultPropertyValuesScript (can be found in tests)
   */
  @Nullable
  private static Map<String, String> getDefaultPropertyValues(String classQualifiedName) {
    Map<String, Map<String, String>> values = SoftReference.dereference(ourDefaultPropertyValues);
    if (values == null) {
      values = loadDefaultPropertyValues(JavaFxRedundantPropertyValueInspection.class.getSimpleName() + "8.txt");
      ourDefaultPropertyValues = new SoftReference<>(values);
    }
    return values.get(classQualifiedName);
  }

  /**
   * The file format is <code>ClassName#propertyName:type=value</code> per line, line with leading double dash (--) is commented out
   */
  @NotNull
  private static Map<String, Map<String, String>> loadDefaultPropertyValues(@NotNull String resourceName) {
    final URL resource = JavaFxRedundantPropertyValueInspection.class.getResource(resourceName);
    if (resource == null) {
      LOG.warn("Resource not found: " + resourceName);
      return Collections.emptyMap();
    }

    final Map<String, Map<String, String>> result = new THashMap<>(200);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), CharsetToolkit.UTF8_CHARSET))) {
      for (String line : FileUtil.loadLines(reader)) {
        if (line.isEmpty() || line.startsWith("--")) continue;
        boolean lineParsed = false;
        final int p1 = line.indexOf('#');
        if (p1 > 0 && p1 < line.length()) {
          final String className = line.substring(0, p1);
          final int p2 = line.indexOf('=', p1);
          if (p2 > p1 && p2 < line.length()) {
            final String propertyName = line.substring(p1 + 1, p2);
            final String valueText = line.substring(p2 + 1);
            lineParsed = true;
            final Map<String, String> properties = result.computeIfAbsent(className, ignored -> new THashMap<>());
            if (properties.put(propertyName, valueText) != null) {
              LOG.warn("Duplicate default property value " + line);
            }
          }
        }
        if (!lineParsed) {
          LOG.warn("Can't parse default property value " + line);
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Can't read resource: " + resourceName, e);
      return Collections.emptyMap();
    }
    return result;
  }
}


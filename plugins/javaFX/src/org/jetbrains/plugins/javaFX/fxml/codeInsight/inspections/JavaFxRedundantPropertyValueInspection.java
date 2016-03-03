package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
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
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyElementDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRedundantPropertyValueInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxRedundantPropertyValueInspection.class.getName());

  private static Reference<Map<String, Map<String, Object>>> ourDefaultPropertyValues;

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

        final Object defaultValue = getDefaultValue(attributeName, attribute.getParent());
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
        if (!(descriptor instanceof JavaFxPropertyElementDescriptor) &&
            !(descriptor instanceof JavaFxClassBackedElementDescriptor)) {
          return;
        }
        if (tag.getSubTags().length != 0) return;
        final String tagText = tag.getValue().getText().trim();
        if (tagText.startsWith("$") ||
            tagText.startsWith("#") ||
            tagText.startsWith("%")) {
          return;
        }

        final Object defaultValue = getDefaultValue(tag.getName(), tag.getParentTag());
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
  private static Object getDefaultValue(@NotNull String propertyName, @Nullable XmlTag enclosingTag) {
    if (enclosingTag != null) {
      final XmlElementDescriptor descriptor = enclosingTag.getDescriptor();
      if (descriptor != null) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiClass) {
          PsiClass containingClass = ((PsiClass)declaration);
          {
            final Object defaultValue = getDefaultPropertyValue(containingClass.getQualifiedName(), propertyName);
            if (defaultValue != null) return defaultValue;
          }
          final LinkedHashSet<PsiClass> superClasses = InheritanceUtil.getSuperClasses(containingClass);
          for (PsiClass superClass : superClasses) {
            final Object defaultValue = getDefaultPropertyValue(superClass.getQualifiedName(), propertyName);
            if (defaultValue != null) return defaultValue;
          }
        }
      }
    }
    return null;
  }

  private static boolean isEqualValue(@NotNull String attributeValue, @NotNull Object defaultValue) {
    if (defaultValue instanceof String && defaultValue.equals(attributeValue)) return true;
    if (defaultValue instanceof Boolean) return defaultValue == Boolean.valueOf(attributeValue);
    if (defaultValue instanceof Double) {
      try {
        return Double.compare((Double)defaultValue, Double.valueOf(attributeValue)) == 0;
      }
      catch (NumberFormatException ignored) {
        return false;
      }
    }
    if (defaultValue instanceof Integer) {
      try {
        return Integer.compare((Integer)defaultValue, Integer.valueOf(attributeValue)) == 0;
      }
      catch (NumberFormatException ignored) {
        return false;
      }
    }
    return false;
  }

  private static Object getDefaultPropertyValue(String classQualifiedName, String propertyName) {
    final Map<String, Object> values = getDefaultPropertyValues(classQualifiedName);
    return values != null ? values.get(propertyName) : null;
  }

  /**
   * Load property values config. The config is produced with the script JavaFxGenerateDefaultPropertyValuesScript (can be found in tests)
   */
  private static Map<String, Object> getDefaultPropertyValues(String classQualifiedName) {
    if (ourDefaultPropertyValues == null) {
      ourDefaultPropertyValues = loadDefaultPropertyValues(JavaFxRedundantPropertyValueInspection.class.getSimpleName() + "8.txt");
    }
    Map<String, Map<String, Object>> values = SoftReference.dereference(ourDefaultPropertyValues);
    return values != null ? values.get(classQualifiedName) : null;
  }

  /**
   * The file format is <code>ClassName#propertyName:type=value</code> per line, line with leading double dash (--) is commented out
   */
  private static Reference<Map<String, Map<String, Object>>> loadDefaultPropertyValues(String resourceName) {
    final URL resource = JavaFxRedundantPropertyValueInspection.class.getResource(resourceName);
    if (resource == null) {
      LOG.warn("Resource not found: " + resourceName);
      return null;
    }

    final Map<String, Map<String, Object>> result = new THashMap<>(200);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), CharsetToolkit.UTF8_CHARSET))) {
      for (String line : FileUtil.loadLines(reader)) {
        if (line.isEmpty() || line.startsWith("--")) continue;
        boolean lineParsed = false;
        final int p1 = line.indexOf('#');
        if (p1 > 0 && p1 < line.length()) {
          final String className = line.substring(0, p1);
          final int p2 = line.indexOf(':', p1);
          if (p2 > p1 && p2 < line.length()) {
            final String propertyName = line.substring(p1 + 1, p2);
            final int p3 = line.indexOf('=', p2 + 1);
            if (p3 > 0 && p3 < line.length()) {
              final String type = line.substring(p2 + 1, p3);
              final String text = line.substring(p3 + 1);
              final Object value = parseValue(type, text);
              if (value != null) {
                lineParsed = true;
                final Map<String, Object> properties = result.computeIfAbsent(className, ignored -> new THashMap<String, Object>());
                if (properties.put(propertyName, value) != null) {
                  LOG.warn("Duplicate default property value " + line);
                }
              }
            }
          }
        }
        if (!lineParsed) {
          LOG.warn("Can't parse default property value " + line);
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Cannot read resource: " + resourceName, e);
      return null;
    }

    return new SoftReference<Map<String, Map<String, Object>>>(result);
  }

  @Nullable
  private static Object parseValue(String type, String text) {
    try {
      switch (type) {
        case "Boolean":
          return Boolean.valueOf(text);
        case "Integer":
          return Integer.valueOf(text);
        case "Double":
          return Double.valueOf(text);
        case "String":
        case "Enum":
          return text;
        default:
          LOG.warn("Unsupported value type " + type + " for '" + text + "'");
          return null;
      }
    }
    catch (NumberFormatException ignored) {
      LOG.warn("Invalid format of " + type + ": '" + text + "'");
      return null;
    }
  }
}


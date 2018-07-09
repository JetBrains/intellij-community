/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.template;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.GenerationUtil;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import java.io.Serializable;
import java.util.Collections;

/**
 * A template contains the method body and the filename of the resource where
 * the text is stored.
 */
public class TemplateResource implements Serializable {
  private final boolean isDefault;
  private String fileName = "";
  private String template = "";
  private String className;

  public TemplateResource(String fileName, String template, boolean aDefault) {
    isDefault = aDefault;
    this.fileName = fileName;
    this.template = template;
  }

  public TemplateResource(String fileName, String template, boolean isDefault, String className) {
    this(fileName, template, isDefault);
    this.className = className;
  }

  /**
   * Bean constructor
   */
  public TemplateResource() {
    isDefault = false;
  }

  /**
     * Returns the part of s after the token.
     * <p/>
     * <br/>Example:   after("helloWorldThisIsMe", "World") will return "ThisIsMe".
     * <br/>Example:   after("helloWorldThisIsMe", "Dog") will return null.
     *
     * @param s   the string to test.
     * @param token   the token.
     * @return  the part of s that is after the token.
     */
    public static String after(String s, String token) {
        if (s == null) {
            return null;
        }

        int i = s.indexOf(token);
        if (i == -1) {
            return s;
        }

        return s.substring(i + token.length());
    }

  /**
     * Returns the part of s before the token.
     * <p/>
     * <br/>Example:   before("helloWorldThisIsMe", "World") will return "hello".
     * <br/>Example:   before("helloWorldThisIsMe", "Dog") will return "helloWorldThisIsMe".
     * <p/>
     * If the token is not in the string, the entire string is returned.
     *
     * @param s   the string to test.
     * @param token   the token.
     * @return  the part of s that is before the token.
     */
    public static String before(String s, String token) {
        if (s == null) {
            return null;
        }

        int i = s.lastIndexOf(token);
        if (i == -1) {
            return s;
        }

        return s.substring(0, i);
    }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public boolean isDefault() {
    return isDefault;
  }

  /**
   * Get's the javadoc, if any.
   *
   * @return the javadoc, null if no javadoc.
   */
  @Nullable
  public String getJavaDoc() {
    int i = template.trim().startsWith("/*") ? template.indexOf("*/") : -1;
    if (i == -1) {
      return null;
    }

    return template.substring(0, i + 2);
  }

  /**
   * Get's the method body.
   *
   * @return the method body.
   */
  public String getMethodBody() {
    return getMethodBody(template);
  }

  @Nullable
  private static String getMethodBody(String template) {
    String signature = getMethodSignature(template);
    String s = after(template, signature);

    if (s == null) {
      return null;
    }

    // skip the starting and ending { }
    final String trimmed = s.trim();
    return trimmed.substring(1, trimmed.length() - 1);
  }

  /**
   * Gets the method signature
   * <p/>
   * {@code public String toString()}
   */
  public String getMethodSignature() {
    return getMethodSignature(template);
  }

  private static String getMethodSignature(String template) {
    final String trimmed = template.trim();
    String s = trimmed.startsWith("/*") ? after(trimmed, "*/") : trimmed;

    final int indexOf = s.indexOf('{');
    if (indexOf > 0) {
      return s.substring(0, indexOf).trim();
    }

    StringBuffer signature = new StringBuffer();

    String[] lines = s.split("\n");
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("@")) {
        continue;
      }
      signature.append(line);
      if (line.indexOf('{') > -1) {
        break;
      }
    }

    // remove last {
    String result = signature.toString();
    return result.substring(0, result.lastIndexOf("{"));
  }

  /**
   * Gets the method that this template is for (toString)
   */
  public String getTargetMethodName(PsiClass clazz) {
    try {
      String text = GenerationUtil.velocityGenerateCode(clazz, Collections.emptyList(), Collections.emptyMap(), template, 0, true);
      return JavaPsiFacade.getElementFactory(clazz.getProject())
          .createMethodFromText(text, clazz, PsiUtil.getLanguageLevel(clazz))
          .getName();
    }
    catch (GenerateCodeException | IncorrectOperationException ignore) {
      return null;
    }
  }

  /**
   * Validates this template to see if its valid for plugin v3.10 or higher.
   *
   * @return true if valid, false if not
   */
  public boolean isValidTemplate() {
    return isValidTemplate(template);
  }

  /**
   * Validates the provided template.
   *
   * @param template the template to validate.
   * @return true if valid, false if not.
   */
  public static boolean isValidTemplate(String template) {
    template = template.trim();

    if (template.indexOf('{') == -1) {
      return false;
    }

    // ending } must be the last character
    String s = template.trim();
    if (s.lastIndexOf('}') != s.length() - 1) {
      return false;
    }

    if (getMethodSignature(template) == null) {
      return false;
    }

    if (getMethodBody(template) == null) {
      return false;
    }

    return true;
  }

  /**
   * Class fqn to detect applicability 
   */
  public String getClassName() {
    return className;
  }

  /**
   * Important to return filename only as it is the display name in the UI.
   *
   * @return filename for UI.
   */
  public String toString() {
    return fileName != null ? fileName : template;
  }

  public String getName() {
    return fileName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateResource)) return false;

    TemplateResource that = (TemplateResource)o;

    return fileName.equals(that.fileName) && template.equals(that.template);
  }

  @Override
  public int hashCode() {
    return 31 * fileName.hashCode() + template.hashCode();
  }
}

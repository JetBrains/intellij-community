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
package org.jetbrains.generate.tostring.template;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.generate.tostring.exception.TemplateResourceException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Resource locator for default method body templates.
 */
public class TemplateResourceLocator {

  private static final String DEFAULT_CONCAT = "/org/jetbrains/generate/tostring/template/DefaultConcatMember.vm";
  private static final String DEFAULT_CONCAT_GROOVY = "/org/jetbrains/generate/tostring/template/DefaultConcatMemberGroovy.vm";
  private static final String DEFAULT_CONCAT_SUPER = "/org/jetbrains/generate/tostring/template/DefaultConcatMemberSuper.vm";
  private static final String DEFAULT_BUFFER = "/org/jetbrains/generate/tostring/template/DefaultBuffer.vm";
  private static final String DEFAULT_BUILDER = "/org/jetbrains/generate/tostring/template/DefaultBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER = "/org/jetbrains/generate/tostring/template/DefaultToStringBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER3 = "/org/jetbrains/generate/tostring/template/DefaultToStringBuilder3.vm";
  private static final String DEFAULT_GUAVA = "/org/jetbrains/generate/tostring/template/DefaultGuava.vm";

  private TemplateResourceLocator() {}

  /**
   * Get the default templates.
   */
  public static TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[]{
        new TemplateResource("String concat (+)", readFile(DEFAULT_CONCAT), true),
        new TemplateResource("String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER), true),
        new TemplateResource("StringBuffer", readFile(DEFAULT_BUFFER), true),
        new TemplateResource("StringBuilder (JDK 1.5)", readFile(DEFAULT_BUILDER), true),
        new TemplateResource("ToStringBuilder (Apache commons-lang)", readFile(DEFAULT_TOSTRINGBUILDER), true),
        new TemplateResource("ToStringBuilder (Apache commons-lang 3)", readFile(DEFAULT_TOSTRINGBUILDER3), true),
        new TemplateResource("Objects.toStringHelper (Guava)", readFile(DEFAULT_GUAVA), true),
        new TemplateResource("Groovy: String concat (+)", readFile(DEFAULT_CONCAT_GROOVY), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  /**
   * Reads the content of the resource and return it as a String.
   * <p/>Uses the class loader that loaded this class to find the resource in its classpath.
   *
   * @param resource the resource name. Will lookup using the classpath.
   * @return the content if the resource
   * @throws IOException error reading the file.
   */
  private static String readFile(String resource) throws IOException {
    BufferedInputStream in = new BufferedInputStream(TemplateResourceLocator.class.getResourceAsStream(resource));
    return StringUtil.convertLineSeparators(FileUtil.loadTextAndClose(new InputStreamReader(in, "UTF-8")));
  }
}

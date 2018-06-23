/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.template.toString;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;

@State(name = "ToStringTemplates", storages = @Storage("toStringTemplates.xml"))
public class ToStringTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_CONCAT = "DefaultConcatMember.vm";
  private static final String DEFAULT_CONCAT_GROOVY = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberGroovy.vm";
  private static final String DEFAULT_CONCAT_SUPER = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberSuper.vm";
  private static final String DEFAULT_CONCAT_SUPER_GROOVY = "/org/jetbrains/java/generate/template/toString/DefaultConcatMemberSuperGroovy.vm";
  private static final String DEFAULT_BUFFER = "/org/jetbrains/java/generate/template/toString/DefaultBuffer.vm";
  private static final String DEFAULT_BUILDER = "/org/jetbrains/java/generate/template/toString/DefaultBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER = "/org/jetbrains/java/generate/template/toString/DefaultToStringBuilder.vm";
  private static final String DEFAULT_TOSTRINGBUILDER3 = "/org/jetbrains/java/generate/template/toString/DefaultToStringBuilder3.vm";
  private static final String DEFAULT_GUAVA = "/org/jetbrains/java/generate/template/toString/DefaultGuava.vm";
  private static final String DEFAULT_GUAVA_18 = "/org/jetbrains/java/generate/template/toString/DefaultGuava18.vm";
  private static final String DEFAULT_STRING_JOINER = "/org/jetbrains/java/generate/template/toString/StringJoiner.vm";

  public static TemplatesManager getInstance() {
    return ServiceManager.getService(ToStringTemplatesManager.class);
  }

  @Override
  public TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[]{
        new TemplateResource("String concat (+)", readFile(DEFAULT_CONCAT), true),
        new TemplateResource("String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER), true),
        new TemplateResource("StringBuffer", readFile(DEFAULT_BUFFER), true),
        new TemplateResource("StringBuilder (JDK 1.5)", readFile(DEFAULT_BUILDER), true),
        new TemplateResource("ToStringBuilder (Apache commons-lang)", readFile(DEFAULT_TOSTRINGBUILDER), true, "org.apache.commons.lang.builder.ToStringBuilder"),
        new TemplateResource("ToStringBuilder (Apache commons-lang 3)", readFile(DEFAULT_TOSTRINGBUILDER3), true, "org.apache.commons.lang3.builder.ToStringBuilder"),
        new TemplateResource("Objects.toStringHelper (Guava)", readFile(DEFAULT_GUAVA), true, "com.google.common.base.Objects"),
        new TemplateResource("MoreObjects.toStringHelper (Guava 18+)", readFile(DEFAULT_GUAVA_18), true, "com.google.common.base.MoreObjects"),
        new TemplateResource("StringJoiner (JDK 1.8)", readFile(DEFAULT_STRING_JOINER), true),
        new TemplateResource("Groovy: String concat (+)", readFile(DEFAULT_CONCAT_GROOVY), true),
        new TemplateResource("Groovy: String concat (+) and super.toString()", readFile(DEFAULT_CONCAT_SUPER_GROOVY), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  protected static String readFile(String resource) throws IOException {
    return readFile(resource, ToStringTemplatesManager.class);
  }
}

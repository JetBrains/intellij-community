/*
 * Copyright 2001-2007 the original author or authors.
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

import org.jetbrains.generate.tostring.exception.TemplateResourceException;
import org.jetbrains.generate.tostring.util.FileUtil;

import java.io.IOException;

/**
 * Resource locator for default method body templates and user specific.
 * <p/>
 * Will scan the 'tostring-plugin' folder of the IDEA plugins folder for additional templates.
 */
public class TemplateResourceLocator {
    /** Foldername for additional velocity body templates. The folder should be a subfolder in the IDEA/<i>plugins</i> folder. */
    public static final String FOLDER_NAME = "tostring-plugin";

    /** Filename for autosaving active template. */
    public static final String AUTOSAVE_ACTIVE_TEMPLATE_FILE_NAME = "__autosave_active";

    private static final String DEFAULT_CONCAT = "/org/jetbrains/generate/tostring/template/DefaultConcatMember.vm";
    private static final String DEFAULT_CONCAT_SUPER = "/org/jetbrains/generate/tostring/template/DefaultConcatMemberSuper.vm";
    private static final String DEFAULT_BUFFER = "/org/jetbrains/generate/tostring/template/DefaultBuffer.vm";
    private static final String DEFAULT_BUILDER = "/org/jetbrains/generate/tostring/template/DefaultBuilder.vm";
    private static final String DEFAULT_TOSTRINGBUILDER = "/org/jetbrains/generate/tostring/template/DefaultToStringBuilder.vm";

    /**
     * Only static methods.
     */
    private TemplateResourceLocator() {
    }


    /**
     * Get the default templates.
     */
    public static TemplateResource[] getDefaultTemplates() {
        try {
            TemplateResource tr1 = new TemplateResource("String concat (+)", FileUtil.readFile(DEFAULT_CONCAT), true);
            TemplateResource tr2 = new TemplateResource("String concat (+) and super.toString()", FileUtil.readFile(DEFAULT_CONCAT_SUPER), true);
            TemplateResource tr3 = new TemplateResource("StringBuffer", FileUtil.readFile(DEFAULT_BUFFER), true);
            TemplateResource tr4 = new TemplateResource("StringBuilder (JDK 1.5)", FileUtil.readFile(DEFAULT_BUILDER), true);
            TemplateResource tr5 = new TemplateResource("ToStringBuilder (Apache Commons)", FileUtil.readFile(DEFAULT_TOSTRINGBUILDER), true);

            return new TemplateResource[]{tr1, tr2, tr3, tr4, tr5};

        } catch (IOException e) {
            throw new TemplateResourceException("Error loading default templates", e);
        }
    }


}

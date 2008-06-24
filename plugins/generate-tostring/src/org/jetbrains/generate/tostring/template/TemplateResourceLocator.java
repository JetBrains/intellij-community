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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.generate.tostring.exception.TemplateResourceException;
import org.jetbrains.generate.tostring.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource locator for default method body templates and user specific.
 * <p/>
 * Will scan the 'tostring-plugin' folder of the IDEA plugins folder for additional templates.
 */
public class TemplateResourceLocator {

    private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.template.TemplateResourceLocator");

    /** Foldername for additional velocity body templates. The folder should be a subfolder in the IDEA/<i>plugins</i> folder. */
    public static final String FOLDER_NAME = "tostring-plugin";

    /** Filename for autosaving active template. */
    public static final String AUTOSAVE_ACTIVE_TEMPLATE_FILE_NAME = "__autosave_active";

    private static final String DEFAULT_CONCAT = "/org/jetbrains/generate/tostring/template/DefaultConcatMember.vm";
    private static final String DEFAULT_CONCAT_SUPER = "/org/jetbrains/generate/tostring/template/DefaultConcatMemberSuper.vm";
    private static final String DEFAULT_BUFFER = "/org/jetbrains/generate/tostring/template/DefaultBuffer.vm";
    private static final String DEFAULT_BUILDER = "/org/jetbrains/generate/tostring/template/DefaultBuilder.vm";
    private static final String DEFAULT_BUILDER_ANNOTATION = "/org/jetbrains/generate/tostring/template/DefaultBuilderWithAnnotation.vm";
    private static final String DEFAULT_TOSTRINGBUILDER = "/org/jetbrains/generate/tostring/template/DefaultToStringBuilder.vm";

    /**
     * Only static methods.
     */
    private TemplateResourceLocator() {
    }


    /**
     * Get's the default template if none exists. Likely when this plugin has been installed for the first time.
     */
    public static String getDefaultTemplateBody() {
        return getDefaultTemplates()[0].getTemplate();
    }

    /**
     * Get's the default template name if none exists. Likely when this plugin has been installed for the first time.
     */ 
    public static String getDefaultTemplateName() {
        return "User template";
    }

    /**
     * Get the default templates.
     */
    public static TemplateResource[] getDefaultTemplates() {
        try {
            TemplateResource tr1 = new TemplateResource("Default using String concat (+)", FileUtil.readFile(DEFAULT_CONCAT));
            TemplateResource tr2 = new TemplateResource("Default using String concat (+) and super.toString()", FileUtil.readFile(DEFAULT_CONCAT_SUPER));
            TemplateResource tr3 = new TemplateResource("Default using StringBuffer", FileUtil.readFile(DEFAULT_BUFFER));
            TemplateResource tr4 = new TemplateResource("Default using StringBuilder (JDK 1.5)", FileUtil.readFile(DEFAULT_BUILDER));
            TemplateResource tr5 = new TemplateResource("Default using StringBuilder with Override Annotation (JDK 1.5)", FileUtil.readFile(DEFAULT_BUILDER_ANNOTATION));
            TemplateResource tr6 = new TemplateResource("Default using org.apache.commons.lang.ToStringBuilder", FileUtil.readFile(DEFAULT_TOSTRINGBUILDER));

            return new TemplateResource[]{tr1, tr2, tr3, tr4, tr5, tr6};

        } catch (IOException e) {
            throw new TemplateResourceException("Error loading default templates", e);
        }
    }

    /**
     * Get the additional user specific templates from the 'tostring-plugin' subfolder.
     *
     * @return additional templates, null or empty array if none exists.
     */
    public static TemplateResource[] getAdditionalTemplates() {
        String path = getTemplateFolder();

        // check for sub folder exists
        File dir = new File(path);
        if (! dir.exists()) {
            return null;
        }

        // add each file in the folder
        List<TemplateResource> resources = new ArrayList<TemplateResource>();
        File[] files = dir.listFiles();
        for (File file : files) {
            // add file if it is not the autosaved template
            if (!file.getName().startsWith(AUTOSAVE_ACTIVE_TEMPLATE_FILE_NAME)) {
                String body;
                try {
                    body = FileUtil.readFile(file);
                } catch (IOException e) {
                    throw new TemplateResourceException("Error loading additional templates", e);
                }
                TemplateResource tr = new TemplateResource(file.getName(), body);
                resources.add(tr);
            }
        }

        return resources.toArray(new TemplateResource[resources.size()]);
    }

    /**
     * Get all the templates (defaults and additional)
     * @return  all the templates.
     */
    public static TemplateResource[] getAllTemplates() {
        List<TemplateResource> resources = new ArrayList<TemplateResource>();

        TemplateResource[] tr1 = getDefaultTemplates();
        for (int i = 0; tr1 != null && i < tr1.length; i++) {
            TemplateResource tr = tr1[i];
            resources.add(tr);
        }

        TemplateResource[] tr2 = getAdditionalTemplates();
        for (int i = 0; tr2 != null && i < tr2.length; i++) {
            TemplateResource tr = tr2[i];
            resources.add(tr);
        }

       return resources.toArray(new TemplateResource[resources.size()]);
    }

    /**
     * Get's the template folder where additional templates are stored.
     * @return  the absolute foldername.
     */
    public static String getTemplateFolder() {
        return PathManager.getPluginsPath() + File.separatorChar + FOLDER_NAME;
    }

    /**
     * Creates the template folder if missing.
     */
    public static void createTemplateFolderIfMissing() {
        String path = getTemplateFolder();

        // check for template folder exists
        File dir = new File(path);
        if (! dir.exists()) {
            log.info("Creating template folder: " + path);
            if (! dir.mkdirs()) {
                log.error("Error creating template folder: " + path);
                throw new RuntimeException("Error creating template folder: " + path);
            }
        }
    }


}

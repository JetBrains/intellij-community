// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.maddyhome.idea.copyright.pattern;

import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.JdkLogChute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public final class VelocityHelper
{
  public static String evaluate(@Nullable PsiFile file, @Nullable Project project, @Nullable Module module, @NotNull String template) {
    return evaluate(file, project, module, template, null);
  }

  public static String evaluate(@Nullable PsiFile file, @Nullable Project project, @Nullable Module module, @NotNull String template, @Nullable String oldComment) {
        VelocityEngine engine = getEngine();
    
        VelocityContext vc = new VelocityContext();
        vc.put("today", new DateInfo());
        if (file != null) vc.put("file", new FileInfo(file));
        if (project != null) vc.put("project", new ProjectInfo(project));
        if (module != null) vc.put("module", new ModuleInfo(module));
        vc.put("username", System.getProperty("user.name"));
   
        vc.put("originalComment", new CommentInfo(oldComment));

        if (file != null) {
          final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
          if (virtualFile != null) {
            final CopyrightVariablesProvider variablesProvider = CopyrightVariablesProviders.INSTANCE.forFileType(virtualFile.getFileType());
            if (variablesProvider != null) {
              final Map<String, Object> context = new HashMap<>();
              variablesProvider.collectVariables(context, project, module, file);
              for (Map.Entry<String, Object> entry : context.entrySet()) {
                vc.put(entry.getKey(), entry.getValue());
              }
            }
          }
        }


      try
        {
          StringWriter sw = new StringWriter();
          boolean stripLineBreak = false;
          if (template.endsWith("$")) {
              template += getVelocitySuffix();
              stripLineBreak = true;
          }
          engine.evaluate(vc, sw, CopyrightManager.class.getName(), template);
          final String result = sw.getBuffer().toString();
          return stripLineBreak ? StringUtil.trimEnd(result, getVelocitySuffix()) : result;
        }
        catch (Exception e)
        {
            return "";
        }
    }

  private static String getVelocitySuffix() {
    return "\n";
  }

  public static void verify(String text) throws Exception
    {
        VelocityEngine engine = getEngine();

        VelocityContext vc = new VelocityContext();
        vc.put("today", new DateInfo());
        StringWriter sw = new StringWriter();
        if (text.endsWith("$")) {
            text += getVelocitySuffix();
        }
        engine.evaluate(vc, sw, CopyrightManager.class.getName(), text);
    }

    private static synchronized VelocityEngine getEngine()
    {
        if (instance == null)
        {
            try
            {
                VelocityEngine engine = new VelocityEngine();
                ExtendedProperties extendedProperties = new ExtendedProperties();

                extendedProperties.addProperty(RuntimeConstants.RESOURCE_LOADER, "file");
                extendedProperties.addProperty(RuntimeConstants.PARSER_POOL_SIZE, "1");

                extendedProperties.addProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
                extendedProperties.addProperty("file.resource.loader.path", PathManager.getPluginsPath() + "/Copyright/resources");

                extendedProperties.addProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                    JdkLogChute.class.getName());
                extendedProperties
                    .addProperty(JdkLogChute.RUNTIME_LOG_JDK_LOGGER, CopyrightManager.class.getName());

                engine.setExtendedProperties(extendedProperties);
                engine.init();

                instance = engine;
            }
            catch (Exception ignored)
            {
            }
        }

        return instance;
    }

    private VelocityHelper()
    {
    }

    private static VelocityEngine instance;
}
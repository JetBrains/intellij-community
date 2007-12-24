package com.maddyhome.idea.copyright.util;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightManager;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.SimpleLog4JLogSystem;

import java.io.StringWriter;

public class VelocityHelper
{
    public static String evaluate(PsiFile file, Project project, Module module, String template)
    {
        VelocityEngine engine = getEngine();

        VelocityContext vc = new VelocityContext();
        vc.put("today", new DateInfo());
        vc.put("file", new FileInfo(file));
        vc.put("project", new ProjectInfo(project));
        vc.put("module", new ModuleInfo(module));
        vc.put("username", System.getProperty("user.name"));

        try
        {
            StringWriter sw = new StringWriter();
            engine.evaluate(vc, sw, CopyrightManager.class.getName(), template);

            return sw.getBuffer().toString();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    public static boolean verify(String text) throws Exception
    {
        VelocityEngine engine = getEngine();

        VelocityContext vc = new VelocityContext();
        vc.put("today", new DateInfo());
        StringWriter sw = new StringWriter();
        return engine.evaluate(vc, sw, CopyrightManager.class.getName(), text);
    }

    private static synchronized VelocityEngine getEngine()
    {
        if (instance == null)
        {
            try
            {
                VelocityEngine engine = new VelocityEngine();
                ExtendedProperties extendedProperties = new ExtendedProperties();

                extendedProperties.addProperty(VelocityEngine.RESOURCE_LOADER, "file");

                extendedProperties.addProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
                extendedProperties.addProperty("file.resource.loader.path", PathManager.getPluginsPath() + "/Copyright/resources");

                extendedProperties.addProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM_CLASS,
                    SimpleLog4JLogSystem.class.getName());
                extendedProperties
                    .addProperty("runtime.log.logsystem.log4j.category", CopyrightManager.class.getName());

                engine.setExtendedProperties(extendedProperties);
                engine.init();

                instance = engine;
            }
            catch (Exception e)
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
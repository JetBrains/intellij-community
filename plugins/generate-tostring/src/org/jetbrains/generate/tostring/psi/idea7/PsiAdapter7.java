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
package org.jetbrains.generate.tostring.psi.idea7;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.generate.tostring.psi.PsiAdapter;

import java.io.File;

/**
 * IDEA 7.x specific implementation.
 */
public class PsiAdapter7 extends PsiAdapter {

    public String getPluginFilename() {
	    Application application = ApplicationManager.getApplication();
	    IdeaPluginDescriptor[] decs = application.getPlugins();

        for (IdeaPluginDescriptor dec : decs) {
            if ("GenerateToString".equals(dec.getName())) {
                return PathManager.getPluginsPath() + File.separatorChar + dec.getPath().getName();
            }
        }

        return null;
    }

}

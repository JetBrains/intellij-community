/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package org.jetbrains.java.generate.template;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public abstract class TemplatesManager implements PersistentStateComponent<TemplatesState> {

  private TemplatesState myState = new TemplatesState();

  public abstract TemplateResource[] getDefaultTemplates();
  /**
   * Reads the content of the resource and return it as a String.
   * <p/>Uses the class loader that loaded this class to find the resource in its classpath.
   *
   * @param resource the resource name. Will lookup using the classpath.
   * @return the content if the resource
   * @throws java.io.IOException error reading the file.
   */
  protected static String readFile(String resource, Class<? extends TemplatesManager> templatesManagerClass) throws IOException {
    BufferedInputStream in = new BufferedInputStream(templatesManagerClass.getResourceAsStream(resource));
    return StringUtil.convertLineSeparators(FileUtil.loadTextAndClose(new InputStreamReader(in, CharsetToolkit.UTF8_CHARSET)));
  }

  public TemplatesState getState() {
        return myState;
    }

    public void loadState(TemplatesState state) {
        myState = state;
    }

    public void addTemplate(TemplateResource template) {
        myState.templates.add(template);
    }

    public void removeTemplate(TemplateResource template) {
        final Iterator<TemplateResource> it = myState.templates.iterator();
        while (it.hasNext()) {
            TemplateResource resource = it.next();
            if (Comparing.equal(resource.getFileName(), template.getFileName())) {
                it.remove();
            }
        }
    }

  public Collection<TemplateResource> getAllTemplates() {
    HashSet<String> names = new HashSet<String>();
    Collection<TemplateResource> templates = new LinkedHashSet<TemplateResource>(Arrays.asList(getDefaultTemplates()));
    for (TemplateResource template : myState.templates) {
      if (names.add(template.getFileName())) {
        templates.add(template);
      }
    }
    return templates;
  }

  public TemplateResource getDefaultTemplate() {
        for (TemplateResource template : getAllTemplates()) {
            if (Comparing.equal(template.getFileName(), myState.defaultTempalteName)) {
                return template;
            }
        }

        return getAllTemplates().iterator().next();
    }


    public void setDefaultTemplate(TemplateResource res) {
        myState.defaultTempalteName = res.getFileName();
    }

    public void setTemplates(List<TemplateResource> items) {
        myState.templates.clear();
        for (TemplateResource item : items) {
            if (!item.isDefault()) {
                myState.templates.add(item);
            }
        }
    }
}
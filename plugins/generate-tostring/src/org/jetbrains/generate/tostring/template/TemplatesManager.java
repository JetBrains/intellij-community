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
package org.jetbrains.generate.tostring.template;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Comparing;

import java.util.*;

@State(
  name = "ToStringTemplates",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/toStringTemplates.xml"
    )}
)
public class TemplatesManager implements PersistentStateComponent<TemplatesState> {
    public static TemplatesManager getInstance() {
        return ServiceManager.getService(TemplatesManager.class);
    }

    private TemplatesState myState = new TemplatesState();

    public TemplatesManager() {
        for (TemplateResource o : TemplateResourceLocator.getDefaultTemplates()) {
            addTemplate(o);
        }
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
    TemplateResource[] defaultTemplates = TemplateResourceLocator.getDefaultTemplates();
    HashSet<String> names = new HashSet<String>();
    for (TemplateResource defaultTemplate : defaultTemplates) {
      names.add(defaultTemplate.getFileName());
    }
    Collection<TemplateResource> templates = new LinkedHashSet<TemplateResource>(Arrays.asList(defaultTemplates));
    for (TemplateResource template : myState.templates) {
      if (!names.contains(template.getFileName())) {
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
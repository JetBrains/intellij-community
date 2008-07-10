/*
 * @author max
 */
package org.jetbrains.generate.tostring.template;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Comparing;

import java.util.*;

@State(
  name = "ToStringTemplates",
  storages = {
    @Storage(
      id ="toStringTemplates",
      file = "$APP_CONFIG$/toStringTemplates.xml"
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
        Collection<TemplateResource> templates = new LinkedHashSet<TemplateResource>(Arrays.asList(TemplateResourceLocator.getDefaultTemplates()));
        templates.addAll(myState.templates);
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
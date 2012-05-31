/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.generate.tostring.view;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.NamedItemsListEditor;
import com.intellij.openapi.ui.Namer;
import com.intellij.openapi.util.Cloner;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.template.TemplatesManager;

import java.util.ArrayList;

public class TemplatesPanel extends NamedItemsListEditor<TemplateResource> {
    private static final Namer<TemplateResource> NAMER = new Namer<TemplateResource>() {
        public String getName(TemplateResource templateResource) {
            return templateResource.getFileName();
        }

        public boolean canRename(TemplateResource item) {
            return !item.isDefault();
        }

        public void setName(TemplateResource templateResource, String name) {
            templateResource.setFileName(name);
        }
    };

    private static final Factory<TemplateResource> FACTORY = new Factory<TemplateResource>() {
        public TemplateResource create() {
            return new TemplateResource();
        }
    };

    private static final Cloner<TemplateResource> CLONER = new Cloner<TemplateResource>() {
        public TemplateResource cloneOf(TemplateResource templateResource) {
            if (templateResource.isDefault()) return templateResource;
            return copyOf(templateResource);
        }

        public TemplateResource copyOf(TemplateResource templateResource) {
            TemplateResource result = new TemplateResource();
            result.setFileName(templateResource.getFileName());
            result.setTemplate(templateResource.getTemplate());
            return result;
        }
    };

    private static final Equality<TemplateResource> COMPARER = new Equality<TemplateResource>() {
        public boolean equals(TemplateResource o1, TemplateResource o2) {
            return Comparing.equal(o1.getTemplate(), o2.getTemplate()) && Comparing.equal(o1.getFileName(), o2.getFileName());
        }
    };

    public TemplatesPanel() {
        super(NAMER, FACTORY, CLONER, COMPARER,
                new ArrayList<TemplateResource>(TemplatesManager.getInstance().getAllTemplates()));

        //ServiceManager.getService(project, MasterDetailsStateService.class).register("ToStringTemplates.UI", this);
    }

    @Nls
    public String getDisplayName() {
        return "Templates";
    }

  @Override
    protected String subjDisplayName() {
        return "template";
    }

    @Nullable
    @NonNls
    public String getHelpTopic() {
        return null;
    }

    @Override
    public boolean isModified() {
        return super.isModified() || !Comparing.equal(TemplatesManager.getInstance().getDefaultTemplate(), getSelectedItem());
    }

    @Override
    protected boolean canDelete(TemplateResource item) {
        return !item.isDefault();
    }

    protected UnnamedConfigurable createConfigurable(TemplateResource item) {
        return new ToStringTemplateConfigurable(item);
    }

    @Override
    public void apply() throws ConfigurationException {
        super.apply();
        TemplatesManager.getInstance().setTemplates(getItems());
        final TemplateResource selection = getSelectedItem();
        if (selection != null) {
            TemplatesManager.getInstance().setDefaultTemplate(selection);
        }
    }
}

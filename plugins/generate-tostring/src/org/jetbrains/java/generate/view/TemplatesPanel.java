// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package org.jetbrains.java.generate.view;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedItemsListEditor;
import com.intellij.openapi.ui.Namer;
import com.intellij.openapi.util.Cloner;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class TemplatesPanel extends NamedItemsListEditor<TemplateResource> {
    private static final Namer<TemplateResource> NAMER = new Namer<TemplateResource>() {
        @Override
        public String getName(TemplateResource templateResource) {
            return templateResource.getFileName();
        }

        @Override
        public boolean canRename(TemplateResource item) {
            return !item.isDefault();
        }

        @Override
        public void setName(TemplateResource templateResource, String name) {
            templateResource.setFileName(name);
        }
    };

    private static final Factory<TemplateResource> FACTORY = () -> new TemplateResource();

    private static final Cloner<TemplateResource> CLONER = new Cloner<TemplateResource>() {
        @Override
        public TemplateResource cloneOf(TemplateResource templateResource) {
            if (templateResource.isDefault()) return templateResource;
            return copyOf(templateResource);
        }

        @Override
        public TemplateResource copyOf(TemplateResource templateResource) {
            TemplateResource result = new TemplateResource();
            result.setFileName(templateResource.getFileName());
            result.setTemplate(templateResource.getTemplate());
            return result;
        }
    };

    private static final Equality<TemplateResource> COMPARER =
      (o1, o2) -> Objects.equals(o1.getTemplate(), o2.getTemplate()) && Objects.equals(o1.getFileName(), o2.getFileName());
  private final Project myProject;
  private final TemplatesManager myTemplatesManager;
  private String myHint;

  public TemplatesPanel(Project project) {
    this(project, ToStringTemplatesManager.getInstance());
  }

  public TemplatesPanel(Project project, TemplatesManager templatesManager) {
        super(NAMER, FACTORY, CLONER, COMPARER,
              new ArrayList<>(templatesManager.getAllTemplates()));

        //ServiceManager.getService(project, MasterDetailsStateService.class).register("ToStringTemplates.UI", this);
    myProject = project;
    myTemplatesManager = templatesManager;
  }

    public void setHint(String hint) {
      myHint = hint;
    }

    @Override
    @Nls
    public String getDisplayName() {
      return JavaBundle.message("configurable.TemplatesPanel.display.name");
    }

  @Override
    protected String subjDisplayName() {
        return "template";
    }

    @Override
    @Nullable
    @NonNls
    public String getHelpTopic() {
        return "Templates_Dialog";
    }

    @Override
    public boolean isModified() {
        return super.isModified() || !Comparing.equal(myTemplatesManager.getDefaultTemplate(), getSelectedItem());
    }

    @Override
    protected boolean canDelete(TemplateResource item) {
        return !item.isDefault();
    }

    @Override
    protected UnnamedConfigurable createConfigurable(TemplateResource item) {
      final GenerateTemplateConfigurable configurable =
        new GenerateTemplateConfigurable(item, Collections.emptyMap(), myProject, onMultipleFields());
      configurable.setHint(myHint);
      return configurable;
    }

    protected boolean onMultipleFields() {
      return true;
    }

    @Override
    public void apply() throws ConfigurationException {
        super.apply();
        myTemplatesManager.setTemplates(getItems());
        final TemplateResource selection = getSelectedItem();
        if (selection != null) {
            myTemplatesManager.setDefaultTemplate(selection);
        }
    }
}

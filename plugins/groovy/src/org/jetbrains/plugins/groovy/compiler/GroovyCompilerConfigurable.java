// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.options.JavaCompilersTab;
import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/**
 * @author peter
 */
public class GroovyCompilerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private JPanel myMainPanel;
  private JPanel myExcludesPanel;
  private JBCheckBox myInvokeDynamicSupportCB;
  private TextFieldWithBrowseButton myConfigScriptPath;
  private JPanel myPathPanel;

  private final ExcludedEntriesConfigurable myExcludes;
  private final GroovyCompilerConfiguration myConfig;

  public GroovyCompilerConfigurable(Project project) {
    myProject = project;
    myConfig = GroovyCompilerConfiguration.getInstance(project);
    myExcludes = createExcludedConfigurable(project);

    myExcludesPanel.setBorder(IdeBorderFactory.createTitledBorder(GroovyBundle.message("settings.compiler.exclude.from.stub.generation"), false, JBUI.insetsTop(8)).setShowLine(false));
  }

  public ExcludedEntriesConfigurable getExcludes() {
    return myExcludes;
  }

  private ExcludedEntriesConfigurable createExcludedConfigurable(@NotNull Project project) {
    final ExcludesConfiguration configuration = myConfig.getExcludeFromStubGeneration();
    ProjectFileIndex index = project.isDefault() ? null : ProjectRootManager.getInstance(project).getFileIndex();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && (index == null || !index.isExcluded(file));
      }
    };
    descriptor.setRoots(ContainerUtil.concat(ContainerUtil.<Module, List<VirtualFile>>map(ModuleManager.getInstance(project).getModules(),
                                                    module -> ModuleRootManager.getInstance(module)
                                                      .getSourceRoots(JavaModuleSourceRootTypes.SOURCES))));
    return new ExcludedEntriesConfigurable(project, descriptor, configuration);
  }


  @Override
  @NotNull
  public String getId() {
    return "Groovy compiler";
  }

  @Override
  public String getDisplayName() {
    return GroovyBundle.message("configurable.GroovyCompilerConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.projectsettings.compiler.groovy";
  }

  @Override
  public JComponent createComponent() {
    myExcludesPanel.add(myExcludes.createComponent());
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return !Objects.equals(myConfig.getConfigScript(), getExternalizableConfigScript()) ||
           myInvokeDynamicSupportCB.isSelected() != myConfig.isInvokeDynamic() ||
           myExcludes.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myExcludes.apply();
    myConfig.setInvokeDynamic(myInvokeDynamicSupportCB.isSelected());
    myConfig.setConfigScript(getExternalizableConfigScript());
    if (!myProject.isDefault()) {
      BuildManager.getInstance().clearState(myProject);
    }
  }

  @Override
  public void reset() {
    myConfigScriptPath.setText(FileUtil.toSystemDependentName(myConfig.getConfigScript()));
    myInvokeDynamicSupportCB.setSelected(myConfig.isInvokeDynamic());
    myExcludes.reset();
  }

  @Override
  public void disposeUIResources() {
    myExcludes.disposeUIResources();
  }

  @NotNull
  private String getExternalizableConfigScript() {
    return FileUtil.toSystemIndependentName(myConfigScriptPath.getText());
  }

  private void createUIComponents() {
    myPathPanel = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().setDefaultWeightX(1.0).
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultFill(GridBagConstraints.HORIZONTAL);

    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
    myConfigScriptPath = new TextFieldWithBrowseButton();
    myConfigScriptPath.addBrowseFolderListener(null, GroovyBundle.message("settings.compiler.select.path.to.groovy.compiler.configscript"), null, descriptor);

    myPathPanel.add(createTopLabel(), gb.nextLine());
    myPathPanel.add(UI.PanelFactory.panel(myConfigScriptPath).withLabel(GroovyBundle.message("settings.compiler.path.to.configscript")).createPanel(), gb.nextLine().insetTop(13));

    String cbText = GroovyBundle.message("settings.compiler.invoke.dynamic.support");
    TextWithMnemonic parsedText = TextWithMnemonic.parse(cbText);
    myInvokeDynamicSupportCB = new JBCheckBox(parsedText.getText(true));
    myInvokeDynamicSupportCB.setDisplayedMnemonicIndex(parsedText.getMnemonicIndex());
    myPathPanel.add(myInvokeDynamicSupportCB, gb.nextLine().insetTop(8));
  }

  private static JComponent createTopLabel() {
    JEditorPane tipComponent = new JEditorPane();
    tipComponent.setContentType("text/html");
    tipComponent.setEditable(false);
    tipComponent.setEditorKit(HTMLEditorKitBuilder.simple());

    EditorKit kit = tipComponent.getEditorKit();
    if (kit instanceof HTMLEditorKit) {
      StyleSheet css = ((HTMLEditorKit)kit).getStyleSheet();

      css.addRule("a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}");
      css.addRule("a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED) + ";}");
      css.addRule("a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}");
      css.addRule("a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}");
      //css.addRule("body {background-color:#" + ColorUtil.toHex(info.warning ? warningBackgroundColor() : errorBackgroundColor()) + ";}");
    }

    if (tipComponent.getCaret() instanceof DefaultCaret) {
      ((DefaultCaret)tipComponent.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }

    tipComponent.setCaretPosition(0);
    tipComponent.setText(GroovyBundle.message("settings.compiler.alternative"));
    tipComponent.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(tipComponent));
        if (allSettings != null) {
          Configurable javacConfigurable = allSettings.find(JavaCompilersTab.class);
          if (javacConfigurable != null) {
            allSettings.select(javacConfigurable);
          }
        }
      }
    });

    tipComponent.setBorder(null);
    tipComponent.setOpaque(false);

    return tipComponent;
  }
}

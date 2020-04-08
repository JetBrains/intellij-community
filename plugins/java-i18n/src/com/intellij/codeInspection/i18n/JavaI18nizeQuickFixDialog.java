// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.I18nizedTextGenerator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UI;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaI18nizeQuickFixDialog extends I18nizeQuickFixDialog {
  private final PsiLiteralExpression myLiteralExpression;

  private final JLabel myPreviewLabel;
  private final JPanel myHyperLinkPanel;
  private final JPanel myResourceBundleSuggester;
  private EditorComboBox myRBEditorTextField;
  private final JPanel myJavaCodeInfoPanel;
  private final JPanel myPreviewPanel;
  private PsiClassType myResourceBundleType;
  protected final ResourceBundleManager myResourceBundleManager;

  private final boolean myShowJavaCodeInfo;
  private final boolean myShowPreview;

  @NonNls public static final String PROPERTY_KEY_OPTION_KEY = "PROPERTY_KEY";
  @NonNls public static final String RESOURCE_BUNDLE_OPTION_KEY = "RESOURCE_BUNDLE";
  @NonNls public static final String PROPERTY_VALUE_ATTR = "PROPERTY_VALUE";

  public JavaI18nizeQuickFixDialog(@NotNull Project project,
                               @NotNull final PsiFile context,
                               @Nullable final PsiLiteralExpression literalExpression,
                               @NotNull String defaultPropertyValue,
                               DialogCustomization customization,
                               final boolean showJavaCodeInfo,
                               final boolean showPreview) {
    super(project, context, defaultPropertyValue, customization, true);

    ResourceBundleManager resourceBundleManager = null;
    try {
      resourceBundleManager = ResourceBundleManager.getManager(context);
      LOG.assertTrue(resourceBundleManager != null);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      LOG.error(e);
    }
    myResourceBundleManager = resourceBundleManager;

    JavaExtensibilityData data = new JavaExtensibilityData();
    myExtensibilityPanel.setLayout(new BorderLayout());
    myExtensibilityPanel.add(data.myPanel, BorderLayout.CENTER);
    myJavaCodeInfoPanel = data.myJavaCodeInfoPanel;
    myPreviewPanel = data.myPreviewPanel;
    myHyperLinkPanel = data.myHyperLinkPanel;
    myResourceBundleSuggester = data.myResourceBundleSuggester;
    myPreviewLabel = data.myPreviewLabel;

    myLiteralExpression = literalExpression;
    myShowPreview = showPreview;

    myResourceBundleSuggester.setLayout(new BorderLayout());
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    PsiClass resourceBundle = myResourceBundleManager.getResourceBundle();

    myShowJavaCodeInfo = showJavaCodeInfo && myResourceBundleManager.canShowJavaCodeInfo();

    if (myShowJavaCodeInfo) {
      LOG.assertTrue(resourceBundle != null);
      myResourceBundleType = factory.createType(resourceBundle);
      @NonNls String defaultVarName = "resourceBundle";
      final JavaCodeFragmentFactory codeFragmentFactory = JavaCodeFragmentFactory.getInstance(project);
      PsiExpressionCodeFragment expressionCodeFragment =
        codeFragmentFactory.createExpressionCodeFragment(defaultVarName, myLiteralExpression, myResourceBundleType, true);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(expressionCodeFragment);
      myRBEditorTextField = new EditorComboBox(document, myProject, StdFileTypes.JAVA);
      myResourceBundleSuggester.add(UI.PanelFactory.panel(myRBEditorTextField)
                                      .withLabel(JavaI18nBundle.message("i18n.quickfix.code.panel.resource.bundle.expression.label"))
                                      .withComment(JavaI18nBundle.message("comment.if.the.resource.bundle.is.invalid.either.declare.it.as.an.object"))
                                      .createPanel(), BorderLayout.NORTH);
      suggestAvailableResourceBundleExpressions();
      myRBEditorTextField.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          somethingChanged();
        }
      });
    }

    myHyperLinkPanel.setLayout(new BorderLayout());
    final String templateName = getTemplateName();

    if (templateName != null) {
      HyperlinkLabel link = new HyperlinkLabel(JavaI18nBundle.message("i18nize.dialog.template.link.label"));
      link.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          final FileTemplateConfigurable configurable = new FileTemplateConfigurable(myProject);
          final FileTemplate template = FileTemplateManager.getInstance(myProject).getCodeTemplate(templateName);
          SwingUtilities.invokeLater(() -> configurable.setTemplate(template, null));
          boolean ok = ShowSettingsUtil.getInstance().editConfigurable(myPanel, configurable);
          if (ok) {
            FileTemplateManager.getInstance(myProject).saveAllTemplates();
            somethingChanged();
            if (myShowJavaCodeInfo) {
              suggestAvailableResourceBundleExpressions();
            }
          }
        }
      });
      myHyperLinkPanel.add(link, BorderLayout.CENTER);
    }

    if (!myShowJavaCodeInfo) {
      myJavaCodeInfoPanel.setVisible(false);
    }
    if (!myShowPreview) {
      myPreviewPanel.setVisible(false);
    }

    init();
  }

  public static boolean isAvailable(PsiFile file) {
    final Project project = file.getProject();
    final String title = JavaI18nBundle.message("i18nize.dialog.error.jdk.title");
    try {
      return ResourceBundleManager.getManager(file) != null;
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      final IntentionAction fix = e.getFix();
      if (fix != null) {
        if (Messages.showOkCancelDialog(project, e.getMessage(), title, Messages.getErrorIcon()) == Messages.OK) {
          try {
            fix.invoke(project, null, file);
            return false;
          }
          catch (IncorrectOperationException e1) {
            LOG.error(e1);
          }
        }
      }
      Messages.showErrorDialog(project, e.getMessage(), title);
      return false;
    }
  }

  public PropertyCreationHandler getPropertyCreationHandler() {
    PropertyCreationHandler handler = myResourceBundleManager.getPropertyCreationHandler();
    return handler != null ? handler : JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER;
  }

  private void suggestAvailableResourceBundleExpressions() {
    String templateName = getTemplateName();
    if (templateName == null) return;

    if (myShowJavaCodeInfo) {
      FileTemplate template = FileTemplateManager.getInstance(myProject).getCodeTemplate(templateName);
      boolean showResourceBundleSuggester = template.getText().contains("${" + RESOURCE_BUNDLE_OPTION_KEY + "}");
      myJavaCodeInfoPanel.setVisible(showResourceBundleSuggester);
    }
    Set<String> result = JavaI18nUtil.suggestExpressionOfType(myResourceBundleType, myLiteralExpression);
    if (result.isEmpty()) {
      result.add(getResourceBundleText());
    }

    myRBEditorTextField.setHistory(ArrayUtilRt.toStringArray(result));
    SwingUtilities.invokeLater(() -> myRBEditorTextField.setSelectedIndex(0));
  }

  @Override
  protected void somethingChanged() {
    if (myShowPreview) {
      myPreviewLabel.setText(getI18nizedText());
    }
    super.somethingChanged();
  }

  @Nullable
  protected String getTemplateName() {
    return myResourceBundleManager.getTemplateName();
  }

  @Override
  protected String defaultSuggestPropertyKey(String value) {
    return myResourceBundleManager.suggestPropertyKey(value);
  }

  @Override
  protected List<String> defaultSuggestPropertiesFiles() {
    return myResourceBundleManager.suggestPropertiesFiles(myContextModules);
  }

  public @NotNull String getI18nizedText() {
    String propertyKey = StringUtil.escapeStringCharacters(getKey());
    I18nizedTextGenerator textGenerator = myResourceBundleManager.getI18nizedTextGenerator();
    if (textGenerator != null) {
      return generateText(textGenerator, propertyKey, getPropertiesFile(), myLiteralExpression);
    }

    String templateName = getTemplateName();
    LOG.assertTrue(templateName != null);
    FileTemplate template = FileTemplateManager.getInstance(myProject).getCodeTemplate(templateName);
    Map<String, String> attributes = new THashMap<>();
    attributes.put(PROPERTY_KEY_OPTION_KEY, propertyKey);
    attributes.put(RESOURCE_BUNDLE_OPTION_KEY, getResourceBundleText());
    attributes.put(PROPERTY_VALUE_ATTR, StringUtil.escapeStringCharacters(myDefaultPropertyValue));
    addAdditionalAttributes(attributes);
    try {
      return template.getText(attributes);
    }
    catch (IOException e) {
      LOG.error(e);
      return "";
    }
  }

  protected String generateText(final I18nizedTextGenerator textGenerator,
                                @NotNull String propertyKey,
                                final PropertiesFile propertiesFile,
                                final PsiLiteralExpression literalExpression) {
    return textGenerator.getI18nizedText(propertyKey, propertiesFile, literalExpression);
  }

  protected void addAdditionalAttributes(final Map<String, String> attributes) {
  }

  private String getResourceBundleText() {
    return myShowJavaCodeInfo ? myRBEditorTextField.getText() : null;
  }

  public PsiLiteralExpression getLiteralExpression() {
    return myLiteralExpression;
  }

  public PsiExpression[] getParameters() {
    return PsiExpression.EMPTY_ARRAY;
  }

  static class JavaExtensibilityData {
    private JPanel myPreviewPanel;
    private JPanel myJavaCodeInfoPanel;
    private JPanel myPanel;
    private JPanel myHyperLinkPanel;
    private MultiLineLabel myPreviewLabel;
    private JPanel myResourceBundleSuggester;
  }
}

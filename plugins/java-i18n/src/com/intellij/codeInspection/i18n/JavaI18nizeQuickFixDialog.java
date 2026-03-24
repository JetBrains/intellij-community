// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.I18nizedTextGenerator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.uast.UExpression;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;

public class JavaI18nizeQuickFixDialog<T extends UExpression> extends I18nizeQuickFixDialog {
  private static final String RESOURCE_BUNDLE_EXPRESSION_USED = "RESOURCE_BUNDLE_EXPRESSION_USED";
  private final T myLiteralExpression;

  private final JLabel myPreviewLabel;
  private final JPanel myHyperLinkPanel;
  private final JPanel myResourceBundleSuggester;
  private EditorComboBox myRBEditorTextField;
  private final JPanel myJavaCodeInfoPanel;
  private final JPanel myPreviewPanel;
  private PsiClassType myResourceBundleType;
  final ResourceBundleManager myResourceBundleManager;

  private final boolean myShowJavaCodeInfo;
  private final boolean myShowPreview;

  private final ExecutorService myExecutorPool = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "JavaI18nizeQuickFixDialog Executor Pool", AppExecutorUtil.getAppExecutorService(), 1);

  public static final @NonNls String PROPERTY_KEY_OPTION_KEY = "PROPERTY_KEY";
  public static final @NonNls String RESOURCE_BUNDLE_OPTION_KEY = "RESOURCE_BUNDLE";
  public static final @NonNls String PROPERTY_VALUE_ATTR = "PROPERTY_VALUE";

  public JavaI18nizeQuickFixDialog(@NotNull Project project,
                                   final @NotNull PsiFile context,
                                   final @Nullable T literalExpression,
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
      @NonNls String defaultVarName = PropertiesComponent.getInstance(myProject).getValue(RESOURCE_BUNDLE_EXPRESSION_USED, "resourceBundle");
      final JavaCodeFragmentFactory codeFragmentFactory = JavaCodeFragmentFactory.getInstance(project);
      PsiExpressionCodeFragment expressionCodeFragment =
        codeFragmentFactory.createExpressionCodeFragment(defaultVarName, myLiteralExpression.getSourcePsi(), myResourceBundleType, true);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(expressionCodeFragment);
      myRBEditorTextField = new EditorComboBox(document, myProject, JavaFileType.INSTANCE);
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

  PropertyCreationHandler getPropertyCreationHandler() {
    if (useExistingProperty()) {
      return JavaI18nUtil.EMPTY_CREATION_HANDLER;
    }
    PropertyCreationHandler handler = myResourceBundleManager.getPropertyCreationHandler();
    return handler != null ? handler : JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER;
  }

  private void suggestAvailableResourceBundleExpressions() {
    String templateName = getTemplateName();
    if (templateName == null) return;

    if (myShowJavaCodeInfo) {
      myJavaCodeInfoPanel.setVisible(showResourceBundleTextField(templateName, myProject));
    }

    ReadAction
      .nonBlocking(() -> JavaI18nUtil.suggestExpressionOfType(myResourceBundleType, myLiteralExpression.getSourcePsi()))
      .finishOnUiThread(ModalityState.any(), suggestedBundles -> {
        if (suggestedBundles.isEmpty()) {
          suggestedBundles.add(getResourceBundleText());
          ContainerUtil.addIfNotNull(suggestedBundles, PropertiesComponent.getInstance(myProject).getValue(RESOURCE_BUNDLE_EXPRESSION_USED));
        }
        myRBEditorTextField.setHistory(ArrayUtilRt.toStringArray(suggestedBundles));
        myRBEditorTextField.setSelectedIndex(0);
      })
      .submit(PooledThreadExecutor.INSTANCE);
  }

  @Override
  protected void doOKAction() {
    if (myShowJavaCodeInfo) {
      PropertiesComponent.getInstance(myProject).setValue(RESOURCE_BUNDLE_EXPRESSION_USED, myRBEditorTextField.getText());
    }
    super.doOKAction();
  }

  public static boolean showResourceBundleTextField(String templateName, Project project) {
    FileTemplate template = FileTemplateManager.getInstance(project).getCodeTemplate(templateName);
    return template.getText().contains("${" + RESOURCE_BUNDLE_OPTION_KEY + "}");
  }

  @Override
  protected void somethingChanged() {
    if (myShowPreview) {
      ReadAction
        .nonBlocking(() -> getI18nizedText())
        .finishOnUiThread(ModalityState.stateForComponent(myPreviewLabel), (@NlsSafe String text) -> myPreviewLabel.setText(text))
        .submit(myExecutorPool);
    }
    super.somethingChanged();
  }

  protected @Nullable String getTemplateName() {
    return myResourceBundleManager.getTemplateName();
  }

  @Override
  protected String escapeValue(String value, @NotNull PsiFile context) {
    try {
      ResourceBundleManager manager = ResourceBundleManager.getManager(context);
      return manager != null ? manager.escapeValue(value) : super.escapeValue(value, context);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      return super.escapeValue(value, context);
    }
  }

  @Override
  protected String defaultSuggestPropertyKey(String value) {
    return myResourceBundleManager.suggestPropertyKey(value);
  }

  @Override
  protected List<String> defaultSuggestPropertiesFiles() {
    return myResourceBundleManager.suggestPropertiesFiles(myContextModules);
  }

  public @NotNull @NlsSafe String getI18nizedText() {
    String propertyKey = StringUtil.escapeStringCharacters(getKey());
    I18nizedTextGenerator textGenerator = myResourceBundleManager.getI18nizedTextGenerator();
    if (textGenerator != null) {
      return generateText(textGenerator, propertyKey, ContainerUtil.getFirstItem(getAllPropertiesFiles(), null), myLiteralExpression.getSourcePsi());
    }

    String templateName = getTemplateName();
    LOG.assertTrue(templateName != null);
    FileTemplate template = FileTemplateManager.getInstance(myProject).getCodeTemplate(templateName);
    Map<String, String> attributes = new HashMap<>();
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
                                @NotNull @NlsSafe String propertyKey,
                                final PropertiesFile propertiesFile,
                                final PsiElement context) {
    return textGenerator.getI18nizedText(propertyKey, propertiesFile, context);
  }

  protected void addAdditionalAttributes(final Map<String, String> attributes) {
  }

  private String getResourceBundleText() {
    return myShowJavaCodeInfo ? myRBEditorTextField.getText() : null;
  }

  public T getLiteralExpression() {
    return myLiteralExpression;
  }

  public UExpression[] getParameters() {
    return new UExpression[0];
  }

  static class JavaExtensibilityData {
    private final JPanel myPreviewPanel;
    private final JPanel myJavaCodeInfoPanel;
    private final JPanel myPanel;
    private final JPanel myHyperLinkPanel;
    private final MultiLineLabel myPreviewLabel;
    private final JPanel myResourceBundleSuggester;

    public JavaExtensibilityData() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        myPanel.setMinimumSize(new Dimension(400, 400));
        myPanel.setPreferredSize(new Dimension(400, 400));
        myPreviewPanel = new JPanel();
        myPreviewPanel.setLayout(new GridLayoutManager(2, 1, new Insets(5, 5, 5, 5), -1, -1));
        myPreviewPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithoutIndent");
        myPanel.add(myPreviewPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        new Dimension(100, -1), null, null, 0, false));
        myPreviewPanel.setBorder(IdeBorderFactory.PlainSmallWithoutIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                             this.$$$getMessageFromBundle$$$(
                                                                                               "messages/JavaI18nBundle",
                                                                                               "i18n.quickfix.preview.panel.title"),
                                                                                             TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                             TitledBorder.DEFAULT_POSITION, null, null));
        myPreviewLabel = new MultiLineLabel();
        myPreviewLabel.setText("####");
        myPreviewPanel.add(myPreviewLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1,
                                                               GridConstraints.SIZEPOLICY_FIXED, new Dimension(75, -1), null, null, 0,
                                                               false));
        myHyperLinkPanel = new JPanel();
        myPreviewPanel.add(myHyperLinkPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                 new Dimension(45, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myJavaCodeInfoPanel = new JPanel();
        myJavaCodeInfoPanel.setLayout(new GridLayoutManager(1, 1, new Insets(5, 5, 5, 5), 4, -1));
        myJavaCodeInfoPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
        myPanel.add(myJavaCodeInfoPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(100, -1), null, null, 0,
                                                             false));
        myJavaCodeInfoPanel.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                               this.$$$getMessageFromBundle$$$(
                                                                                                 "messages/JavaI18nBundle",
                                                                                                 "i18n.quickfix.code.panel.title"),
                                                                                               TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                               TitledBorder.DEFAULT_POSITION, null, null));
        myResourceBundleSuggester = new JPanel();
        myJavaCodeInfoPanel.add(myResourceBundleSuggester,
                                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      }
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myPanel; }
  }
}

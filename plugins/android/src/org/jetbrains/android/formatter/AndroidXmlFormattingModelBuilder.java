package org.jetbrains.android.formatter;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XmlFormattingModelBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.color.ColorDomFileDescription;
import org.jetbrains.android.dom.drawable.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlFormattingModelBuilder implements CustomFormattingModelBuilder {
  private final XmlFormattingModelBuilder myXmlFormattingModelBuilder = new XmlFormattingModelBuilder();

  @Override
  public boolean isEngagedToFormat(PsiElement context) {
    return getContextSpecificSettings(context) != null;
  }

  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    final FormattingModel baseModel = myXmlFormattingModelBuilder.createModel(element, settings);
    final AndroidXmlCodeStyleSettings baseSettings = AndroidXmlCodeStyleSettings.getInstance(settings);

    if (!baseSettings.USE_CUSTOM_SETTINGS) {
      return baseModel;
    }
    final ContextSpecificSettingsProviders.Provider provider = getContextSpecificSettings(element);
    final AndroidXmlCodeStyleSettings.MySettings s = provider != null ? provider.getSettings(baseSettings) : null;
    return s != null ? new AndroidXmlFormattingModel(baseModel, settings, s) : baseModel;
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return myXmlFormattingModelBuilder.getRangeAffectingIndent(file, offset, elementAtOffset);
  }

  @Nullable
  private static ContextSpecificSettingsProviders.Provider getContextSpecificSettings(PsiElement context) {
    final PsiFile file = context.getContainingFile();

    if (!(file instanceof XmlFile) ||
        AndroidFacet.getInstance(file) == null) {
      return null;
    }
    final DomFileDescription<?> description = DomManager.getDomManager(
      context.getProject()).getDomFileDescription((XmlFile)file);
    if (description instanceof LayoutDomFileDescription) {
      return ContextSpecificSettingsProviders.LAYOUT;
    }
    else if (description instanceof ManifestDomFileDescription) {
      return ContextSpecificSettingsProviders.MANIFEST;
    }
    else if (description instanceof ResourcesDomFileDescription ||
             description instanceof DrawableStateListDomFileDescription ||
             description instanceof ColorDomFileDescription) {
      return ContextSpecificSettingsProviders.VALUE_RESOURCE_FILE;
    }
    else if (description instanceof AndroidResourceDomFileDescription) {
      return ContextSpecificSettingsProviders.OTHER;
    }
    return null;
  }
}

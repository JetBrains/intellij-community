package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class HtmlLabelsHolder {
  private static final Key<CachedValue<HtmlLabelsHolder>> htmlLabelsHolderKey = Key.create("html labels holder");
  private final Set<String> myForValuesOfLabels = new HashSet<>();

  private static final UserDataCache<CachedValue<HtmlLabelsHolder>, XmlFile, Object> CACHE =
    new UserDataCache<>() {
      @Override
      protected CachedValue<HtmlLabelsHolder> compute(final XmlFile file, final Object p) {
        return CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
          final HtmlLabelsHolder holder = new HtmlLabelsHolder();
          final Language language = file.getViewProvider().getBaseLanguage();
          final PsiFile psiFile = file.getViewProvider().getPsi(language);
          if (psiFile != null) {
            psiFile.accept(new LabelGatheringRecursiveVisitor(holder));
            return new CachedValueProvider.Result<>(holder, file);
          }
          return null;
        }, false);
      }
    };

  public static HtmlLabelsHolder getInstance(final XmlFile file) {
    return CACHE.get(htmlLabelsHolderKey, file, null).getValue();
  }

  private void registerForValue(@NotNull String forValue) {
    myForValuesOfLabels.add(forValue);
  }

  public boolean hasForValue(@NotNull String forValue) {
    return myForValuesOfLabels.contains(forValue);
  }

  private static final class LabelGatheringRecursiveVisitor extends XmlRecursiveElementVisitor {
    private final HtmlLabelsHolder myHolder;

    private LabelGatheringRecursiveVisitor(HtmlLabelsHolder holder) {
      super(true);
      myHolder = holder;
    }

    @Override
    public void visitXmlTag(@NotNull XmlTag tag) {
      super.visitXmlTag(tag);
      if ("label".equals(StringUtil.toLowerCase(tag.getName()))) {
        for (XmlAttribute attribute : tag.getAttributes()) {
          if ("for".equals(StringUtil.toLowerCase(attribute.getLocalName()))) {
            String id = attribute.getValue();
            if (id != null) {
              myHolder.registerForValue(id);
            }
          }
        }
      }
    }
  }
}

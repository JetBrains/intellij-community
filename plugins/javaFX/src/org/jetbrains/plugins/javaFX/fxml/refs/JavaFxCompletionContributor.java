package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;

import java.util.Arrays;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author yole
 */
public class JavaFxCompletionContributor extends CompletionContributor {
  public JavaFxCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inside(xmlTag()), new JavaFxTagCompletionContributor());
  }

  private static class JavaFxTagCompletionContributor extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference instanceof JavaFxTagNameReference) {
        addJavaFxTagVariants((JavaFxTagNameReference)reference, result);
        result.stopHere();
      }
    }

    private static void addJavaFxTagVariants(JavaFxTagNameReference reference, CompletionResultSet result) {
      final XmlTag xmlTag = (XmlTag)reference.getElement();

      List<String> namespaces = Arrays.asList(xmlTag.knownNamespaces());
      final List<XmlElementDescriptor> variants = TagNameVariantCollector.getTagDescriptors(xmlTag, namespaces, null);
      for (XmlElementDescriptor descriptor : variants) {
        final String descriptorName = descriptor.getName(reference.getElement());
        if (descriptorName != null) {
          LookupElementBuilder lookupElement = LookupElementBuilder.create(descriptor, descriptorName);
          result.addElement(lookupElement.withInsertHandler(JavaFxTagInsertHandler.INSTANCE));
        }
      }
    }
  }

  private static class JavaFxTagInsertHandler extends XmlTagInsertHandler {
    public static final JavaFxTagInsertHandler INSTANCE = new JavaFxTagInsertHandler();

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);
      final Object object = item.getObject();
      if (object instanceof JavaFxClassBackedElementDescriptor) {
        final XmlFile xmlFile = (XmlFile)context.getFile();
        final String shortName = ((JavaFxClassBackedElementDescriptor)object).getName();
        context.commitDocument();
        JavaFxPsiUtil.insertImportWhenNeeded(xmlFile, shortName, ((JavaFxClassBackedElementDescriptor)object).getQualifiedName());
      }
    }
  }
}

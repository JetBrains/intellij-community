package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: anna
 * Date: 1/8/13
 */
public class JavaFxTagNameReference extends TagNameReference {
  public JavaFxTagNameReference(ASTNode element, boolean startTagFlag) {
    super(element, startTagFlag);
  }

  @NotNull
  @Override
  public LookupElement[] getVariants() {
    final PsiElement element = getElement();
    if(!myStartTagFlag){
      return super.getVariants();
    }
    final XmlTag xmlTag = (XmlTag)element;
    
    final List<XmlElementDescriptor>
      variants = TagNameReference.<XmlElementDescriptor>getTagNameVariants(xmlTag, Arrays.asList(xmlTag.knownNamespaces()), new ArrayList<String>(), Function.ID);
    final List<LookupElement> elements = new ArrayList<LookupElement>(variants.size());
    for (XmlElementDescriptor descriptor : variants) {
      LookupElementBuilder lookupElement = LookupElementBuilder.create(descriptor, descriptor.getName(element));
      elements.add(lookupElement.withInsertHandler(JavaFxTagInsertHandler.INSTANCE));
    }
    return elements.toArray(new LookupElement[elements.size()]);
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
        if (shortName != null && JavaFxClassBackedElementDescriptor.findPsiClass(shortName, xmlFile.getRootTag()) == null) {
          final XmlProcessingInstruction processingInstruction = JavaFxPsiUtil
            .createSingleImportInstruction(((JavaFxClassBackedElementDescriptor)object).getQualifiedName(), context.getProject());
          final XmlDocument document = xmlFile.getDocument();
          if (document != null) {
            final XmlProlog prolog = document.getProlog();
            if (prolog != null) {
              prolog.add(processingInstruction);
            } else {
              document.addBefore(processingInstruction, document.getRootTag());
            }
            context.commitDocument();
            PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());
          }
        }
      }
    }
  }
}

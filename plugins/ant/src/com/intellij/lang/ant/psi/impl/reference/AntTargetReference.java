package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.AntAntImpl;
import com.intellij.lang.ant.quickfix.AntChangeContextFix;
import com.intellij.lang.ant.quickfix.AntCreateTargetFix;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AntTargetReference extends AntGenericReference {
  private boolean myShouldBeSkippedByAnnotator;
  private static final NotNullFunction<AntTarget,LookupElement> VARIANT_MAPPER = new NotNullFunction<AntTarget, LookupElement>() {
    @NotNull
    public LookupElement fun(final AntTarget antTarget) {
      return LookupElementFactory.getInstance().createLookupElement(antTarget).setTailType(TailType.NONE);
    }
  };

  public AntTargetReference(final AntElement antElement, final String str, final TextRange textRange, final XmlAttribute attribute) {
    super(antElement, str, textRange, attribute);
    setShouldBeSkippedByAnnotator(false);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    if (element instanceof AntProject || element instanceof AntCall || element instanceof AntAnt) {
      getAttribute().setValue(newElementName);
    }
    else if (element instanceof AntTarget) {
      int start = getElementStartOffset() + getReferenceStartOffset() - getAttributeValueStartOffset();
      final String value = getAttribute().getValue();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        if (start > 0) {
          builder.append(value.substring(0, start));
        }
        builder.append(newElementName);
        if (value.length() > start + getRangeInElement().getLength()) {
          builder.append(value.substring(start + getRangeInElement().getLength()));
        }
        getAttribute().setValue(builder.toString());
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    return element;
  }

  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntTarget) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant targets.");
  }


  public PsiElement resolveInner() {
    final String name = getCanonicalRepresentationText();
    if (name == null) return null;

    final AntElement element = getElement();
    final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(element.getProject());
    AntTarget result = null;

    if (element instanceof AntAntImpl) {
      final PsiFile psiFile = ((AntAntImpl)element).getCalledAntFile();
      if (psiFile != null) {
        AntFile antFile = AntSupport.getAntFile(psiFile);
        if (antFile != null) {
          final AntFile context = antConfig.getContextFile(antFile);
          
          assert context != null;

          final AntProject project = context.getAntProject();
          if (project != null) {
            result = resolveTargetImpl(name, project);
          }
        }
      }
    }
    
    if (result == null) {
      final AntFile context = antConfig.getContextFile(element.getAntFile());
      
      assert context != null;

      result = resolveTargetImpl(name, context.getAntProject());
    }

    return result;
  }

  private static AntTarget resolveTargetImpl(final String name, final AntProject project) {
    final AntTarget result = project.getTarget(name);
    if (result != null) {
      return result;
    }
    for (final AntTarget target : project.getImportedTargets()) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    for (final AntTarget target : project.getImportedTargets()) {
      if (name.equals(target.getQualifiedName())) {
        return target;
      }
    }
    return null;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("cannot.resolve.target", getCanonicalRepresentationText());
  }

  public boolean shouldBeSkippedByAnnotator() {
    synchronized (PsiLock.LOCK) {
      return myShouldBeSkippedByAnnotator;
    }
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
    synchronized (PsiLock.LOCK) {
      myShouldBeSkippedByAnnotator = value;
    }
  }

  public Object[] getVariants() {
    final AntElement element = getElement();
    if (element instanceof AntAntImpl) {
      final PsiFile psiFile = ((AntAntImpl)element).getCalledAntFile();
      if (psiFile != null) {
        AntFile antFile;
        if (psiFile instanceof AntFile) {
          antFile = (AntFile)psiFile;
        }
        else {
          antFile = AntSupport.getAntFile(psiFile);
        }
        final AntProject project = (antFile == null) ? null : antFile.getAntProject();
        if (project != null) {
          return ContainerUtil.map2Array(project.getTargets(), LookupElement.class, VARIANT_MAPPER);
        }
      }
    }

    List<AntTarget> result = new ArrayList<AntTarget>();

    final AntProject project = element.getAntProject();
    final AntTarget[] targets = project.getTargets();
    for (final AntTarget target : targets) {
      if (target != element) {
        result.add(target);
      }
    }

    result.addAll(Arrays.asList(project.getImportedTargets()));
    return ContainerUtil.map2Array(result, LookupElement.class, VARIANT_MAPPER);
  }

  @NotNull
  public IntentionAction[] getFixes() {
    final String name = getCanonicalRepresentationText();
    if (name == null || name.length() == 0) return IntentionAction.EMPTY_ARRAY;

    final AntProject project = getElement().getAntProject();
    final AntFile[] importedFiles = project.getImportedFiles();
    final List<IntentionAction> result = new ArrayList<IntentionAction>(importedFiles.length + 1);
    result.add(new AntCreateTargetFix(this));
    for (final AntFile file : importedFiles) {
      if (file.isPhysical()) {
        result.add(new AntCreateTargetFix(this, file));
      }
    }
    result.add(new AntChangeContextFix());
    return result.toArray(new IntentionAction[result.size()]);
  }

  private int getElementStartOffset() {
    return getElement().getTextRange().getStartOffset();
  }

  private int getReferenceStartOffset() {
    return getRangeInElement().getStartOffset();
  }

  private int getAttributeValueStartOffset() {
    final XmlAttribute attr = getAttribute();
    final XmlAttributeValue valueElement = attr.getValueElement();
    return (valueElement == null) ? attr.getTextRange().getEndOffset() + 1 : valueElement.getTextRange().getStartOffset() + 1;
  }
}
package org.jetbrains.android.dom.converters;

import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class FileResourceReference extends BaseResourceReference {
  private final Set<PsiFile> myTargets;

  public FileResourceReference(GenericDomValue<ResourceValue> genericDomValue, Collection<PsiFile> targets) {
    super(genericDomValue);
    myTargets = new HashSet<PsiFile>(targets);
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean b) {
    List<ResolveResult> result = new ArrayList<ResolveResult>();
    for (PsiFile target : myTargets) {
      if (target != null) {
        PsiFile e = new FileResourceElementWrapper(target);
        result.add(new PsiElementResolveResult(e));
      }
    }
    return result.toArray(new ResolveResult[result.size()]);
  }
}

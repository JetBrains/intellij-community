package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public abstract class GroovyCacheUtil {

  @NotNull
  public static GrMember[] getAnnotatedMemberCandidates(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) return GrMember.EMPTY_ARRAY;
    final Collection<GrMember> members = StubIndex.getInstance().get(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope);
    return members.toArray(new GrMember[members.size()]);
  }

  @NotNull
  public static GrTypeDefinition[] getDeriverCandidates(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) return GrTypeDefinition.EMPTY_ARRAY;
    final ArrayList<GrTypeDefinition> inheritors = new ArrayList<GrTypeDefinition>();
    final Collection<GrReferenceList> refLists = StubIndex.getInstance().get(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope);
    for (GrReferenceList list : refLists) {
      final PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition) {
        inheritors.add(((GrTypeDefinition)parent));
      }
    }
    return inheritors.toArray(new GrTypeDefinition[inheritors.size()]);
  }


}

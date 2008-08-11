package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;

import java.util.Collection;

/**
 * @author ilyas
 */
public abstract class GroovyCacheUtil {

  @NotNull
  public static GrMember[] getAnnotatedMembers(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) return GrMember.EMPTY_ARRAY;
    final Collection<GrMember> members = StubIndex.getInstance().get(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope);
    return members.toArray(new GrMember[members.size()]);
  }



}

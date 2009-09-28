package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GrFullClassNameIndex extends IntStubIndexExtension<PsiClass> {
  public static final StubIndexKey<Integer,PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn");

  private static final GrFullClassNameIndex ourInstance = new GrFullClassNameIndex();
  public static GrFullClassNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }

  public Collection<PsiClass> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return super.get(integer, project, new GrSourceFilterScope(scope, project));
  }
}
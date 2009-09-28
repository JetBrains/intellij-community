package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GrFullScriptNameIndex extends IntStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<Integer, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.fqn");

  private static final GrFullScriptNameIndex ourInstance = new GrFullScriptNameIndex();
  public static GrFullScriptNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<Integer, GroovyFile> getKey() {
    return KEY;
  }

  public Collection<GroovyFile> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return super.get(integer, project, new GrSourceFilterScope(scope, project));
  }
}
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.listenerList

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl

@CompileStatic
class ListenerListTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.addFileToProject 'listener.groovy', '''\
class WowEvent {}
interface MyCoolListener {
    void someCoolStuffHappened(WowEvent e)
}
'''
  }

  void 'test all methods resolved'() {
    fixture.with {
      def file = addFileToProject('Bean.groovy', '''\
class Bean {
    @groovy.beans.ListenerList
    List<MyCoolListener> listeners
}
''') as GroovyFileImpl
      assert !file.contentsLoaded

      configureByText '_.groovy', '''\
def bean = new Bean()
bean.addMyCoolListener {}
bean.removeMyCoolListener() {}
bean.getMyCoolListeners()
bean.fireSomeCoolStuffHappened(new WowEvent())
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()
      assert !file.contentsLoaded

      configureByText 'Main.java', '''\
class Main {
  void foo() {
    Bean bean = new Bean();
    bean.addMyCoolListener(e -> {});
    bean.removeMyCoolListener(e -> {});
    MyCoolListener[] actionListeners = bean.getMyCoolListeners();
    bean.fireSomeCoolStuffHappened(new WowEvent());
  }
}
'''
      checkHighlighting()
      assert !file.contentsLoaded
    }
  }

  void 'test with empty custom name'() {
    fixture.with {
      addFileToProject 'Bean.groovy', '''\
class Bean {
    @groovy.beans.ListenerList(name = "")
    List<MyCoolListener> listeners
}
'''

      configureByText '_.groovy', '''\
def bean = new Bean()
bean.addMyCoolListener {}
bean.removeMyCoolListener() {}
bean.getMyCoolListeners()
bean.fireSomeCoolStuffHappened(new WowEvent())
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()

      configureByText 'Main.java', '''\
class Main {
  void foo() {
    Bean bean = new Bean();
    bean.addMyCoolListener(e -> {});
    bean.removeMyCoolListener(e -> {});
    MyCoolListener[] actionListeners = bean.getMyCoolListeners();
    bean.fireSomeCoolStuffHappened(new WowEvent());
  }
}
'''
      checkHighlighting()
    }
  }

  void 'test with custom name'() {
    fixture.with {
      addFileToProject 'Bean.groovy', '''\
class Bean {
    @groovy.beans.ListenerList(name = "awesomeListener")
    List<MyCoolListener> listeners
}
'''

      configureByText '_.groovy', '''\
def bean = new Bean()
bean.addAwesomeListener {}
bean.removeAwesomeListener() {}
bean.getAwesomeListeners()
bean.fireSomeCoolStuffHappened(new WowEvent())
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()

      configureByText 'Main.java', '''\
class Main {
  void foo() {
    Bean bean = new Bean();
    bean.addAwesomeListener(e -> {});
    bean.removeAwesomeListener(e -> {});
    MyCoolListener[] actionListeners = bean.getAwesomeListeners();
    bean.fireSomeCoolStuffHappened(new WowEvent());
  }
}
'''
      checkHighlighting()
    }
  }

  void 'test with spaces custom name'() {
    fixture.with {
      addFileToProject 'Bean.groovy', '''\
class Bean {
    @groovy.beans.ListenerList(name = "  ")
    List<MyCoolListener> listeners
}
'''

      configureByText '_.groovy', '''\
def bean = new Bean()
bean.'add  ' {}
bean.'remove  '() {}
bean.'get  s'()
bean.fireSomeCoolStuffHappened(new WowEvent())
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()
    }
  }

  void 'test errors highlighting'() {
    fixture.with {
      configureByText '_.groovy', '''\
import groovy.beans.ListenerList
class Bean {
  <error descr="@ListenerList field must have a generic Collection type">@ListenerList</error> Object notACollection
  <error descr="@ListenerList field must have a generic Collection type">@ListenerList</error> List rawList
  <error descr="@ListenerList field with generic wildcards not supported">@ListenerList</error> List<?> wildcardList
  <error descr="@ListenerList field with generic wildcards not supported">@ListenerList</error> List<? extends Range> boundedList
  @ListenerList List<Object> okList
}
'''
      checkHighlighting()
    }
  }
}

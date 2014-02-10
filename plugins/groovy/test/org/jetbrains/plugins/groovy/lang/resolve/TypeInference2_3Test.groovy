/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor

/**
 * Created by Max Medvedev on 10/02/14
 */
class TypeInference2_3Test extends TypeInferenceTestBase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3
  }

  public void testContravariantType() throws Exception {
    doTest('''\
import groovy.transform.CompileStatic
import java.util.concurrent.Callable

@CompileStatic
class TestCase {

    interface Action<T> {
        void execute(T thing)
    }

    static class Wrapper<T> {

        private final T thing

        Wrapper(T thing) {
            this.thing = thing
        }

        void contravariantTake(Action<? super T> action) {
            action.execute(thing)
        }

    }

    static <T> Wrapper<T> wrap(Callable<T> callable) {
        new Wrapper(callable.call())
    }

    static Integer dub(Integer integer) {
        integer * 2
    }

    static void main(String[] args) {
        wrap {
            1
        } contravariantTake {
            dub(i<caret>t) // fails static compile, 'it' is not known to be Integer
        }
    }

}
''', 'java.lang.Integer')
  }
}

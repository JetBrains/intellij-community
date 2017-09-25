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
package org.jetbrains.plugins.gradle.tooling.internal

import groovy.json.JsonOutput
import org.gradle.api.Project

/**
 * @author Vladislav.Soroka
 */
class DynamicSettings extends GroovyObjectSupport {
  Map content = [:]
  Project project

  DynamicSettings(Project project) {
    this.project = project
  }

  def methodMissing(String name, def args) {
    if (args.length == 1 && args[0] instanceof Closure) {
      def value = JDelegate.cloneDelegateAndGetContent((Closure)args[0])
      content.put(name, value)
      return content
    }
  }

  def propertyMissing(String name) {
    return content[name] ?: project.findProperty(name)
  }

  @Override
  String toString() {
    return JsonOutput.toJson(content)
  }
}

class JDelegate extends GroovyObjectSupport {
  private Object content

  JDelegate() {
  }

  Object invokeMethod(String name, Object args) {
    Object val = null
    if (args != null && Object[].class.isAssignableFrom(args.getClass())) {
      Object[] arr = (Object[])((Object[])args)
      if (arr.length == 1) {
        val = arr[0]
      }
      else if (isIterableOrArrayAndClosure(arr)) {
        Closure<?> closure = (Closure)arr[1]
        Iterator<?> iterator = arr[0] instanceof Iterable ? ((Iterable)arr[0]).iterator() :
                               Arrays.asList((Object[])((Object[])arr[0])).iterator()
        ArrayList list = new ArrayList()

        while (iterator.hasNext()) {
          list.add(curryDelegateAndGetContent(closure, iterator.next()))
        }

        val = list
      }
      else {
        val = Arrays.asList(arr)
      }
    }

    if (!content) {
      content = new LinkedHashMap()
    }

    if (content instanceof Map) {
      if (content.containsKey(name)) {
        def list = new ArrayList()
        content.each { k, v -> list.add([k, v]) }
        content = list
      }
      else {
        this.content.put(name, val)
      }
    }

    if (content instanceof List) {
      content.add([name, val])
    }
    return val
  }

  static boolean isIterableOrArrayAndClosure(Object[] args) {
    if (args.length == 2 && args[1] instanceof Closure) {
      return args[0] instanceof Iterable || args[0] != null && args[0].getClass().isArray()
    }
    else {
      return false
    }
  }

  static Object cloneDelegateAndGetContent(Closure<?> c) {
    JDelegate delegate = new JDelegate()
    Closure<?> cloned = (Closure)c.clone()
    cloned.setDelegate(delegate)
    cloned.setResolveStrategy(1)
    cloned.call()
    return delegate.getContent()
  }

  static Object curryDelegateAndGetContent(Closure<?> c, Object o) {
    JDelegate delegate = new JDelegate()
    Closure<?> curried = c.curry(o)
    curried.setDelegate(delegate)
    curried.setResolveStrategy(1)
    curried.call()
    return delegate.getContent()
  }

  Object getContent() {
    return this.content
  }
}
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.graph;

import com.intellij.util.graph.impl.GraphAlgorithmsImpl;
import junit.framework.TestCase;

import java.util.*;

/**
 * @author nik
 */
public abstract class GraphTestCase extends TestCase {
  protected static GraphAlgorithmsImpl getAlgorithmsInstance() {
    return new GraphAlgorithmsImpl();
  }

  protected static Graph<String> initGraph(final Map<String, String> graph) {
    final Map<String, List<String>> out = new HashMap<>();
    final Map<String, List<String>> in = new HashMap<>();
    for (String s : graph.keySet()) {
      out.put(s, new ArrayList<>());
      in.put(s, new ArrayList<>());
    }
    for (Map.Entry<String, String> entry : graph.entrySet()) {
      String from = entry.getKey();
      for (int i = 0; i < entry.getValue().length(); i++) {
        String to = String.valueOf(entry.getValue().charAt(i));
        out.get(from).add(to);
        in.get(to).add(from);
      }
    }
    return new Graph<String>() {
      @Override
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      @Override
      public Iterator<String> getIn(final String n) {
        return in.get(n).iterator();
      }

      @Override
      public Iterator<String> getOut(String n) {
        return out.get(n).iterator();
      }
    };
  }
}

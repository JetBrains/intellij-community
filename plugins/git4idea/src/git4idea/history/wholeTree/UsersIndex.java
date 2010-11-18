/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.util.Ref;

import java.util.*;

/**
 * @author irengrig
 * recalculated on each add load
 *
 * common for all repositories
 *
 *
 * todo assert UI access only
 * todo recalculate in presentation; newly added values would not be affected this way
 */
public class UsersIndex {
  private boolean myIsDirty;
  private Map<String, Ref<Integer>> myIndex;
  // they should only grow; can keep it here to not recalculate while not changed
  private final List<String> myKeys;

  protected UsersIndex() {
    myIndex = new HashMap<String, Ref<Integer>>();
    myIsDirty = false;
    myKeys = new ArrayList<String>();
  }

  public Read createRead() {
    return AssertProxy.createAWTAccess(new Read(myIsDirty, myIndex, myKeys));
  }

  public Collection<String> getKeys() {
    return Collections.unmodifiableSet(myIndex.keySet());
  }

  public Ref<Integer> put(final String name) {
    if (! myIndex.containsKey(name)) {
      final Ref<Integer> ref = new Ref<Integer>(-1);
      if (myIsDirty) {
        myIndex.put(name, ref);
        return ref;
      }

      final Map<String, Ref<Integer>> map = myIndex;
      myIndex = new HashMap<String, Ref<Integer>>();
      for (String s : myIndex.keySet()) {
        myIndex.put(s, map.get(s));
      }
      myIndex.put(name, ref);
      myIsDirty = true;
      return ref;
    }
    return myIndex.get(name);
  }

  public static class Read {
    private boolean myIsDirty;
    private final Map<String, Ref<Integer>> myIndex;
    private final List<String> myKeys;

    public Read(boolean isDirty, Map<String, Ref<Integer>> map, final List<String> keys) {
      myIsDirty = isDirty;
      myIndex = map;
      myKeys = keys;
    }

    public void recalculate() {
      myKeys.clear();
      myKeys.addAll(myIndex.keySet());
      Collections.sort(myKeys);

      if (myIsDirty) {
        int i = 0;
        for (String key : myKeys) {
          myIndex.get(key).set(i);
          ++ i;
        }
        myIsDirty = false;
      }
    }

    public String getName(final int idx) {
      return myKeys.get(idx);
    }

    public Ref<Integer> get(final String name) {
      assert ! myIsDirty;
      return myIndex.get(name);
    }
  }
}

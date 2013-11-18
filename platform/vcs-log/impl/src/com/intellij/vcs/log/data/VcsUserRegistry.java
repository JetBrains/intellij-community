/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.util.containers.Interner;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;

import java.util.Set;

/**
 *
 */
public class VcsUserRegistry {

  private final Interner<VcsUser> myUserMap = new Interner<VcsUser>();

  public VcsUser createUser(String name, String email) {
    return myUserMap.intern(new VcsUserImpl(name, email));
  }

  public void addUser(VcsUser user) {
    myUserMap.intern(user);
  }

  public Set<VcsUser> getUsers() {
    return myUserMap.getValues();
  }
}

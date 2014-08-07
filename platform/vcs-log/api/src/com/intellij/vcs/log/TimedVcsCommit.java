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
package com.intellij.vcs.log;

import com.intellij.vcs.log.graph.GraphCommit;

import java.util.List;

/**
 * A typified {@link GraphCommit}.
 * <p/>
 * An instance of this object can be obtained via
 * {@link VcsLogObjectsFactory#createTimedCommit(Hash, List, long) VcsLogObjectsFactory#createTimedCommit}.
 * <p/>
 * It is not recommended to create a custom implementation of this interface, but if you need it, <b>make sure to implement {@code equals()}
 * and {@code hashcode()} so that they consider only the Hash</b>, i.e. two TimedVcsCommits are equal if and only if they have equal
 * hash codes. The VCS Log framework heavily relies on this fact.
 */
public interface TimedVcsCommit extends GraphCommit<Hash> {

}

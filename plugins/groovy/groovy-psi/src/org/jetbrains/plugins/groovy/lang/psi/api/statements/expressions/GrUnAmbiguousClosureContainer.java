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
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.plugins.groovy.annotator.GroovyAnnotatorPre30;

/**
 * Marker interface. Hack for GSP injected expressions with closures
 * @see GroovyAnnotatorPre30#isClosureAmbiguous(org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock)
 * @author Max Medvedev
 */
public interface GrUnAmbiguousClosureContainer {}

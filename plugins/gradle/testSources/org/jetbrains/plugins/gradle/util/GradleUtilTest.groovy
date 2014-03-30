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
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.util.ArtifactInfo
import junit.framework.Assert
import org.junit.Test;

/**
 * @author Denis Zhdanov
 * @since 1/18/13 1:21 PM
 */
class GradleUtilTest {

  // TODO den remove
  @Test
  void "dummy"() {
  }
  
  // TODO den uncomment
//  @Test
//  void "parsing artifact info"() {
//    Assert.assertEquals(new ArtifactInfo('commons-io', null, '1.2'), GradleUtil.parseArtifactInfo("/my/commons-io-1.2.jar"))
//    Assert.assertEquals(new ArtifactInfo('c3p0', null, '2'), GradleUtil.parseArtifactInfo("/my/c3p0-2.jar"))
//    Assert.assertEquals(new ArtifactInfo('c3p0-sources', null, '2'), GradleUtil.parseArtifactInfo("/my/c3p0-sources-2.zip"))
//    Assert.assertEquals(new ArtifactInfo('lib', null, '2'), GradleUtil.parseArtifactInfo("lib-2"))
//  }
}

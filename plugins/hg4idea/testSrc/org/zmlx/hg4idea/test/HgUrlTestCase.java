// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.test;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.command.HgUrl;

public class HgUrlTestCase {

  @Test
  public void testHttp() throws Exception {
    HgUrl url = new HgUrl("http://vasea:pupkin@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertEquals("pupkin", url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());

    url = new HgUrl("http://:pupkin@host.somewhere.com:8081/hg");
    Assert.assertNull(url.getUsername());
    Assert.assertEquals("pupkin", url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());

    url = new HgUrl("http://vasea@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertNull(url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());

    url = new HgUrl("http://vasea:@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertNull(url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());
  }

  @Test
  public void testHttps() throws Exception {
    HgUrl url = new HgUrl("https://vasea:pupkin@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertEquals("pupkin", url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());

    url = new HgUrl("https://host.somewhere.com:8081/hg");
    Assert.assertNull(url.getUsername());
    Assert.assertNull(url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());
  }

  @Test
  public void testSsh() throws Exception {
    HgUrl url = new HgUrl("ssh://vasea:pupkin@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertEquals("pupkin", url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());

    url = new HgUrl("ssh://vasea@host.somewhere.com:8081/hg");
    Assert.assertEquals("vasea", url.getUsername());
    Assert.assertNull(url.getPassword());
    Assert.assertTrue(url.supportsAuthentication());
  }

  @Test
  public void testFile() throws Exception {
    HgUrl url = new HgUrl("/home/vasea/pupkin");
    Assert.assertFalse(url.supportsAuthentication());

    url = new HgUrl("./vasea/pupkin");
    Assert.assertFalse(url.supportsAuthentication());

    url = new HgUrl("../vasea/pupkin");
    Assert.assertFalse(url.supportsAuthentication());

    url = new HgUrl("file:///vasea/pupkin");
    Assert.assertFalse(url.supportsAuthentication());
  }

}

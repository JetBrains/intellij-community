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
package com.intellij.ide.updates;


import com.intellij.openapi.updateSettings.impl.BuildInfo;
import com.intellij.openapi.updateSettings.impl.Product;
import com.intellij.openapi.updateSettings.impl.UpdateChannel;
import com.intellij.openapi.updateSettings.impl.UpdatesInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.InputStream;
import java.util.Calendar;


@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "ConstantConditions"})
public class UpdatesInfoXppParserTest extends TestCase {

  public void testValidXmlParsing() throws Exception {
    final UpdatesInfo info = InfoReader.read("current.xml");
    Assert.assertNotNull(info);
    Assert.assertEquals(5, info.getProductsCount());

    final Product iu = info.getProduct("IU");

    checkProduct(iu, "IntelliJ IDEA", new String[]{"maiaEAP", "IDEA10EAP", "idea90"});

    final UpdateChannel channel = iu.findUpdateChannelById("IDEA10EAP");
    final BuildInfo build = channel.getLatestBuild();

    Assert.assertEquals(BuildNumber.fromString("98.520"), build.getNumber());
    Calendar releaseDate = Calendar.getInstance();
    releaseDate.setTime(build.getReleaseDate());
    Assert.assertEquals(3, releaseDate.get(Calendar.DAY_OF_MONTH));
    Assert.assertEquals(Calendar.APRIL, releaseDate.get(Calendar.MONTH));
    Assert.assertEquals(2011, releaseDate.get(Calendar.YEAR));

  }

  public void testEmptyChannels() throws Exception {
    final UpdatesInfo info = InfoReader.read("emptyChannels.xml");
    Assert.assertNotNull(info);
    Assert.assertEquals(0,info.getProduct("IU").getChannels().size());
  }

  public void testOneProductOnly() throws Exception {
    final UpdatesInfo info = InfoReader.read("oneProductOnly.xml");
    Assert.assertNotNull(info);
    final Product iu = info.getProduct("IU");

    checkProduct(iu, "IntelliJ IDEA", new String[]{"maiaEAP", "IDEA10EAP", "idea90"});

    final UpdateChannel channel = iu.findUpdateChannelById("IDEA10EAP");
    final BuildInfo build = channel.getLatestBuild();

    Assert.assertEquals(BuildNumber.fromString("98.520"), build.getNumber());
  }

  private static void checkProduct(Product product, String name, @Nullable String[] channels) {
    Assert.assertEquals(name, product.getName());
    if (channels == null) {
      Assert.assertEquals(0, product.getChannels().size());
    }
    else {
      Assert.assertEquals(channels.length, product.getChannels().size());
      for (String channel : channels) {
        Assert.assertNotNull(product.findUpdateChannelById(channel));
      }
    }
  }

  public static class InfoReader {
    public static UpdatesInfo read(String fileName)  {
      final InputStream stream = UpdatesInfoXppParserTest.class.getResourceAsStream(fileName);
      try {
        return new UpdatesInfo(JDOMUtil.loadDocument(stream).getRootElement());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}


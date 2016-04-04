/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.BuildNumber;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UpdatesInfoParserTest {
  @Test
  public void testLiveJetbrainsUpdateFile() throws MalformedURLException {
    UpdatesInfo info = InfoReader.read(new URL("http://www.jetbrains.com/updates/updates.xml"));
    assertNotNull(info.getProduct("IC"));
  }

  @Test
  public void testLiveAndroidUpdateFile() throws MalformedURLException {
    UpdatesInfo info = InfoReader.read(new URL("https://dl.google.com/android/studio/patches/updates.xml"));
    assertNotNull(info.getProduct("AI"));
  }

  @Test
  public void testValidXmlParsing() {
    UpdatesInfo info = InfoReader.read("current.xml");
    assertEquals(5, info.getProductsCount());

    Product product = info.getProduct("IU");
    assertNotNull(product);
    checkProduct(product, "IntelliJ IDEA", "maiaEAP", "IDEA10EAP", "idea90");

    UpdateChannel channel = product.findUpdateChannelById("IDEA10EAP");
    assertNotNull(channel);
    assertEquals(ChannelStatus.EAP, channel.getStatus());
    assertEquals(UpdateChannel.LICENSING_EAP, channel.getLicensing());
    assertNotNull(channel.getHomePageUrl());
    assertNotNull(channel.getFeedbackUrl());

    BuildInfo build = channel.getLatestBuild();
    assertNotNull(build);
    assertEquals(BuildNumber.fromString("98.520"), build.getNumber());
    Date date = build.getReleaseDate();
    assertNotNull(date);
    assertEquals("2011-04-03", new SimpleDateFormat("yyyy-MM-dd").format(date));
  }

  @Test
  public void testEmptyChannels() {
    UpdatesInfo info = InfoReader.read("emptyChannels.xml");

    Product product = info.getProduct("IU");
    assertNotNull(product);
    assertEquals(0, product.getChannels().size());
  }

  @Test
  public void testOneProductOnly() {
    UpdatesInfo info = InfoReader.read("oneProductOnly.xml");
    assertNotNull(info);

    Product product = info.getProduct("IU");
    assertNotNull(product);
    checkProduct(product, "IntelliJ IDEA", "maiaEAP", "IDEA10EAP", "idea90");

    UpdateChannel channel = product.findUpdateChannelById("IDEA10EAP");
    assertNotNull(channel);

    BuildInfo build = channel.getLatestBuild();
    assertNotNull(build);
    assertEquals(BuildNumber.fromString("98.520"), build.getNumber());
  }

  private static void checkProduct(Product product, String name, String... channels) {
    assertEquals(name, product.getName());
    assertEquals(channels.length, product.getChannels().size());
    for (String channel : channels) {
      assertNotNull(product.findUpdateChannelById(channel));
    }
  }
}

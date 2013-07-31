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
package org.jetbrains.io;

import com.intellij.openapi.Disposable;
import com.intellij.util.net.NetUtils;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.CustomPortServerManager;

import java.net.InetSocketAddress;

final class SubServer implements CustomPortServerManager.CustomPortService, Disposable {
  private final ChannelGroup openChannels = new DefaultChannelGroup();
  private final CustomPortServerManager user;
  private final ServerBootstrap bootstrap;

  public SubServer(CustomPortServerManager user, NioServerSocketChannelFactory channelFactory) {
    this.user = user;
    user.setManager(this);
    bootstrap = BuiltInServer.createServerBootstrap(channelFactory, openChannels, user.createXmlRpcHandlers());
  }

  public boolean bind(int port) {
    if (port == BuiltInServerManager.getInstance().getPort()) {
      return true;
    }

    try {
      openChannels.add(bootstrap.bind(user.isAvailableExternally() ? new InetSocketAddress(port) : new InetSocketAddress(NetUtils.getLoopbackAddress(), port)));
      return true;
    }
    catch (Exception e) {
      NettyUtil.log(e, BuiltInServer.LOG);
      user.cannotBind(e, port);
      return false;
    }
  }

  @Override
  public boolean isBound() {
    return !openChannels.isEmpty();
  }

  private void stop() {
    openChannels.close().awaitUninterruptibly();
    openChannels.clear();
  }

  @Override
  public boolean rebind() {
    stop();
    return bind(user.getPort());
  }

  @Override
  public void dispose() {
    stop();
    user.setManager(null);
  }
}
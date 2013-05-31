package org.jetbrains.io;

import com.intellij.openapi.Disposable;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.CustomPortServerManager;

import java.net.InetSocketAddress;

public class SubServer implements CustomPortServerManager.CustomPortService, Disposable {
  private final ChannelGroup openChannels = new DefaultChannelGroup();
  private final CustomPortServerManager user;
  private final ServerBootstrap bootstrap;

  public SubServer(CustomPortServerManager user, ServerBootstrap bootstrap) {
    this.user = user;
    user.setManager(this);
    this.bootstrap = bootstrap;
  }

  public boolean bind(int port) {
    if (port == BuiltInServerManager.getInstance().getPort()) {
      return true;
    }

    try {
      openChannels.add(bootstrap.bind(new InetSocketAddress(port)));
      return true;
    }
    catch (ChannelException e) {
      BuiltInServer.LOG.error(e);
      user.cannotBind(e);
      return false;
    }
  }

  @Override
  public boolean isBound() {
    return !openChannels.isEmpty();
  }

  private void stop() {
    // todo should we call releaseExternalResources? We use only 1 boss&worker thread
    openChannels.close().awaitUninterruptibly();
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
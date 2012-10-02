package org.jetbrains.ide;

import com.intellij.ide.XmlRpcServer;
import com.intellij.ide.XmlRpcServerImpl;
import com.intellij.util.Consumer;
import org.jboss.netty.channel.ChannelPipeline;

class XmlRpcPipelineConsumer implements Consumer<ChannelPipeline> {
  @Override
  public void consume(ChannelPipeline pipeline) {
    XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
    ((XmlRpcServerImpl)xmlRpcServer).consume(pipeline);
  }
}

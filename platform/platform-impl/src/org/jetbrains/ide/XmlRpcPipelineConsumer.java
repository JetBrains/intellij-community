package org.jetbrains.ide;

import com.intellij.ide.XmlRpcServer;
import com.intellij.ide.XmlRpcServerImpl;
import com.intellij.util.Consumer;
import org.jboss.netty.channel.ChannelPipeline;

class XmlRpcPipelineConsumer implements Consumer<ChannelPipeline> {
  private final XmlRpcServerImpl myXmlRpcServer;

  XmlRpcPipelineConsumer(XmlRpcServer xmlRpcServer) {
    myXmlRpcServer = (XmlRpcServerImpl)xmlRpcServer;
  }

  @Override
  public void consume(ChannelPipeline pipeline) {
    myXmlRpcServer.consume(pipeline);
  }
}

package com.trilead.ssh2_build213.transport;

import java.io.IOException;

/**
 * MessageHandler.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: MessageHandler.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public interface MessageHandler
{
	public void handleMessage(byte[] msg, int msglen) throws IOException;
}

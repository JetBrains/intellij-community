
package com.trilead.ssh2_build213.sftp;

/**
 * 
 * Types for the 'type' field in the SFTP ATTRS data type.
 * <p>
 * "<i>On a POSIX system, these values would be derived from the mode field
 * of the stat structure.  SPECIAL should be used for files that are of
 * a known type which cannot be expressed in the protocol. UNKNOWN
 * should be used if the type is not known.</i>"
 *
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: AttribTypes.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 *
 */
public class AttribTypes
{
	public static final int SSH_FILEXFER_TYPE_REGULAR = 1;
	public static final int SSH_FILEXFER_TYPE_DIRECTORY = 2;
	public static final int SSH_FILEXFER_TYPE_SYMLINK = 3;
	public static final int SSH_FILEXFER_TYPE_SPECIAL = 4;
	public static final int SSH_FILEXFER_TYPE_UNKNOWN = 5;
	public static final int SSH_FILEXFER_TYPE_SOCKET = 6;
	public static final int SSH_FILEXFER_TYPE_CHAR_DEVICE = 7;
	public static final int SSH_FILEXFER_TYPE_BLOCK_DEVICE = 8;
	public static final int SSH_FILEXFER_TYPE_FIFO = 9;
}

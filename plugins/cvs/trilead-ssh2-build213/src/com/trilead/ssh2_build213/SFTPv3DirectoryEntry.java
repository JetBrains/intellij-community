
package com.trilead.ssh2_build213;

/**
 * A <code>SFTPv3DirectoryEntry</code> as returned by {@link SFTPv3Client#ls(String)}.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SFTPv3DirectoryEntry.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public class SFTPv3DirectoryEntry
{
	/**
	 *  A relative name within the directory, without any path components.
	 */
	public String filename;

	/**
	 * An expanded format for the file name, similar to what is returned by
	 * "ls -l" on Un*x systems.
	 * <p>
	 * The format of this field is unspecified by the SFTP v3 protocol.
	 * It MUST be suitable for use in the output of a directory listing
	 * command (in fact, the recommended operation for a directory listing
	 * command is to simply display this data).  However, clients SHOULD NOT
	 * attempt to parse the longname field for file attributes; they SHOULD
	 * use the attrs field instead.
	 * <p>
	 * The recommended format for the longname field is as follows:<br>
	 * <code>-rwxr-xr-x   1 mjos     staff      348911 Mar 25 14:29 t-filexfer</code>
	 */
	public String longEntry;

	/**
	 * The attributes of this entry.
	 */
	public SFTPv3FileAttributes attributes;
}

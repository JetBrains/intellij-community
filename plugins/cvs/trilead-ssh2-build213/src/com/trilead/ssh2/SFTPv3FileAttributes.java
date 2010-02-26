
package com.trilead.ssh2;

/**
 * A <code>SFTPv3FileAttributes</code> object represents detail information
 * about a file on the server. Not all fields may/must be present.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SFTPv3FileAttributes.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */

public class SFTPv3FileAttributes
{
	/**
	 * The SIZE attribute. <code>NULL</code> if not present.
	 */
	public Long size = null;

	/**
	 * The UID attribute. <code>NULL</code> if not present.
	 */
	public Integer uid = null;

	/**
	 * The GID attribute. <code>NULL</code> if not present.
	 */
	public Integer gid = null;

	/**
	 * The POSIX permissions. <code>NULL</code> if not present.
	 * <p>
	 * Here is a list:
	 * <p>
	 * <pre>Note: these numbers are all OCTAL.
	 *  
	 *  S_IFMT     0170000   bitmask for the file type bitfields
	 *  S_IFSOCK   0140000   socket
	 *  S_IFLNK    0120000   symbolic link
	 *  S_IFREG    0100000   regular file
	 *  S_IFBLK    0060000   block device
	 *  S_IFDIR    0040000   directory
	 *  S_IFCHR    0020000   character device
	 *  S_IFIFO    0010000   fifo 
	 *  S_ISUID    0004000   set UID bit
	 *  S_ISGID    0002000   set GID bit 
	 *  S_ISVTX    0001000   sticky bit
	 *  
	 *  S_IRWXU    00700     mask for file owner permissions
	 *  S_IRUSR    00400     owner has read permission
	 *  S_IWUSR    00200     owner has write permission
	 *  S_IXUSR    00100     owner has execute permission
	 *  S_IRWXG    00070     mask for group permissions
	 *  S_IRGRP    00040     group has read permission
	 *  S_IWGRP    00020     group has write permission
	 *  S_IXGRP    00010     group has execute permission
	 *  S_IRWXO    00007     mask for permissions for others (not in group)
	 *  S_IROTH    00004     others have read permission
	 *  S_IWOTH    00002     others have write permisson
	 *  S_IXOTH    00001     others have execute permission
	 * </pre>
	 */
	public Integer permissions = null;

	/**
	 * The ATIME attribute. Represented as seconds from Jan 1, 1970 in UTC.
	 * <code>NULL</code> if not present.
	 */
	public Long atime = null;

	/**
	 * The MTIME attribute. Represented as seconds from Jan 1, 1970 in UTC.
	 * <code>NULL</code> if not present.
	 */
	public Long mtime = null;

	/**
	 * Checks if this entry is a directory.
	 * 
	 * @return Returns true if permissions are available and they indicate
	 *         that this entry represents a directory.
	 */
	public boolean isDirectory()
	{
		if (permissions == null)
			return false;
		
		return ((permissions.intValue() & 0040000) != 0);
	}
	
	/**
	 * Checks if this entry is a regular file.
	 * 
	 * @return Returns true if permissions are available and they indicate
	 *         that this entry represents a regular file.
	 */
	public boolean isRegularFile()
	{
		if (permissions == null)
			return false;
		
		return ((permissions.intValue() & 0100000) != 0);
	}
	
	/**
	 * Checks if this entry is a a symlink.
	 * 
	 * @return Returns true if permissions are available and they indicate
	 *         that this entry represents a symlink.
	 */
	public boolean isSymlink()
	{
		if (permissions == null)
			return false;
		
		return ((permissions.intValue() & 0120000) != 0);
	}
	
	/**
	 * Turn the POSIX permissions into a 7 digit octal representation.
	 * Note: the returned value is first masked with <code>0177777</code>.
	 * 
	 * @return <code>NULL</code> if permissions are not available.
	 */
	public String getOctalPermissions()
	{
		if (permissions == null)
			return null;

		String res = Integer.toString(permissions.intValue() & 0177777, 8);

		StringBuffer sb = new StringBuffer();

		int leadingZeros = 7 - res.length();

		while (leadingZeros > 0)
		{
			sb.append('0');
			leadingZeros--;
		}

		sb.append(res);

		return sb.toString();
	}
}

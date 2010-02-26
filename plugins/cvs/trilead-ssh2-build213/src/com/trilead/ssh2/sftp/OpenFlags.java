
package com.trilead.ssh2.sftp;

/**
 *
 * SFTP Open Flags.
 * 
 * The following table is provided to assist in mapping POSIX semantics
 * to equivalent SFTP file open parameters:
 * <p>
 * TODO: This comment should be moved to the open method.
 * <p>
 * <ul>
 * <li>O_RDONLY
 * <ul><li>desired-access = READ_DATA | READ_ATTRIBUTES</li></ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_WRONLY
 * <ul><li>desired-access = WRITE_DATA | WRITE_ATTRIBUTES</li></ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_RDWR
 * <ul><li>desired-access = READ_DATA | READ_ATTRIBUTES | WRITE_DATA | WRITE_ATTRIBUTES</li></ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_APPEND
 * <ul>
 * <li>desired-access = WRITE_DATA | WRITE_ATTRIBUTES | APPEND_DATA</li>
 * <li>flags = SSH_FXF_ACCESS_APPEND_DATA and or SSH_FXF_ACCESS_APPEND_DATA_ATOMIC</li>
 * </ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_CREAT
 * <ul>
 * <li>flags = SSH_FXF_OPEN_OR_CREATE</li>
 * </ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_TRUNC
 * <ul>
 * <li>flags = SSH_FXF_TRUNCATE_EXISTING</li>
 * </ul>
 * </li>
 * </ul>
 * <ul>
 * <li>O_TRUNC|O_CREATE
 * <ul>
 * <li>flags = SSH_FXF_CREATE_TRUNCATE</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: OpenFlags.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class OpenFlags
{
	/**
	 * Disposition is a 3 bit field that controls how the file is opened.
	 * The server MUST support these bits (possible enumaration values:
	 * SSH_FXF_CREATE_NEW, SSH_FXF_CREATE_TRUNCATE, SSH_FXF_OPEN_EXISTING,
	 * SSH_FXF_OPEN_OR_CREATE or SSH_FXF_TRUNCATE_EXISTING).
	 */
	public static final int SSH_FXF_ACCESS_DISPOSITION = 0x00000007;

	/**
	 * A new file is created; if the file already exists, the server
	 * MUST return status SSH_FX_FILE_ALREADY_EXISTS.
	 */
	public static final int SSH_FXF_CREATE_NEW = 0x00000000;

	/**
	 * A new file is created; if the file already exists, it is opened
	 * and truncated.
	 */
	public static final int SSH_FXF_CREATE_TRUNCATE = 0x00000001;

	/**
	 * An existing file is opened.  If the file does not exist, the
	 * server MUST return SSH_FX_NO_SUCH_FILE. If a directory in the
	 * path does not exist, the server SHOULD return
	 * SSH_FX_NO_SUCH_PATH. It is also acceptable if the server
	 * returns SSH_FX_NO_SUCH_FILE in this case.
	 */
	public static final int SSH_FXF_OPEN_EXISTING = 0x00000002;

	/**
	 * If the file exists, it is opened. If the file does not exist,
	 * it is created.
	 */
	public static final int SSH_FXF_OPEN_OR_CREATE = 0x00000003;

	/**
	 * An existing file is opened and truncated. If the file does not
	 * exist, the server MUST return the same error codes as defined
	 * for SSH_FXF_OPEN_EXISTING.
	 */
	public static final int SSH_FXF_TRUNCATE_EXISTING = 0x00000004;

	/**
	 * Data is always written at the end of the file. The offset field
	 * of the SSH_FXP_WRITE requests are ignored.
	 * <p>
	 * Data is not required to be appended atomically. This means that
	 * if multiple writers attempt to append data simultaneously, data
	 * from the first may be lost. However, data MAY be appended
	 * atomically.
	 */
	public static final int SSH_FXF_ACCESS_APPEND_DATA = 0x00000008;

	/**
	 * Data is always written at the end of the file. The offset field
	 * of the SSH_FXP_WRITE requests are ignored.
	 * <p>
	 * Data MUST be written atomically so that there is no chance that
	 * multiple appenders can collide and result in data being lost.
	 * <p>
	 * If both append flags are specified, the server SHOULD use atomic
	 * append if it is available, but SHOULD use non-atomic appends
	 * otherwise. The server SHOULD NOT fail the request in this case.
	 */
	public static final int SSH_FXF_ACCESS_APPEND_DATA_ATOMIC = 0x00000010;

	/**
	 * Indicates that the server should treat the file as text and
	 * convert it to the canonical newline convention in use.
	 * (See Determining Server Newline Convention in section 5.3 in the
	 * SFTP standard draft).
	 * <p>
	 * When a file is opened with this flag, the offset field in the read
	 * and write functions is ignored.
	 * <p>
	 * Servers MUST process multiple, parallel reads and writes correctly
	 * in this mode.  Naturally, it is permissible for them to do this by
	 * serializing the requests.
	 * <p>
	 * Clients SHOULD use the SSH_FXF_ACCESS_APPEND_DATA flag to append
	 * data to a text file rather then using write with a calculated offset.
	 */
	public static final int SSH_FXF_ACCESS_TEXT_MODE = 0x00000020;

	/**
	 * The server MUST guarantee that no other handle has been opened
	 * with ACE4_READ_DATA access, and that no other handle will be
	 * opened with ACE4_READ_DATA access until the client closes the
	 * handle. (This MUST apply both to other clients and to other
	 * processes on the server.)
	 * <p>
	 * If there is a conflicting lock the server MUST return
	 * SSH_FX_LOCK_CONFLICT.  If the server cannot make the locking
	 * guarantee, it MUST return SSH_FX_OP_UNSUPPORTED.
	 * <p>
	 * Other handles MAY be opened for ACE4_WRITE_DATA or any other
	 * combination of accesses, as long as ACE4_READ_DATA is not included
	 * in the mask.
	 */
	public static final int SSH_FXF_ACCESS_BLOCK_READ = 0x00000040;

	/**
	 * The server MUST guarantee that no other handle has been opened
	 * with ACE4_WRITE_DATA or ACE4_APPEND_DATA access, and that no other
	 * handle will be opened with ACE4_WRITE_DATA or ACE4_APPEND_DATA
	 * access until the client closes the handle. (This MUST apply both
	 * to other clients and to other processes on the server.)
	 * <p>
	 * If there is a conflicting lock the server MUST return
	 * SSH_FX_LOCK_CONFLICT. If the server cannot make the locking
	 * guarantee, it MUST return SSH_FX_OP_UNSUPPORTED.
	 * <p>
	 * Other handles MAY be opened for ACE4_READ_DATA or any other
	 * combination of accesses, as long as neither ACE4_WRITE_DATA nor
	 * ACE4_APPEND_DATA are included in the mask.
	 */
	public static final int SSH_FXF_ACCESS_BLOCK_WRITE = 0x00000080;

	/**
	 * The server MUST guarantee that no other handle has been opened
	 * with ACE4_DELETE access, opened with the
	 * SSH_FXF_ACCESS_DELETE_ON_CLOSE flag set, and that no other handle
	 * will be opened with ACE4_DELETE access or with the
	 * SSH_FXF_ACCESS_DELETE_ON_CLOSE flag set, and that the file itself
	 * is not deleted in any other way until the client closes the handle.
	 * <p>
	 * If there is a conflicting lock the server MUST return
	 * SSH_FX_LOCK_CONFLICT.  If the server cannot make the locking
	 * guarantee, it MUST return SSH_FX_OP_UNSUPPORTED.
	 */
	public static final int SSH_FXF_ACCESS_BLOCK_DELETE = 0x00000100;

	/**
	 * If this bit is set, the above BLOCK modes are advisory. In advisory
	 * mode, only other accesses that specify a BLOCK mode need be
	 * considered when determining whether the BLOCK can be granted,
	 * and the server need not prevent I/O operations that violate the
	 * block mode.
	 * <p>
	 * The server MAY perform mandatory locking even if the BLOCK_ADVISORY
	 * bit is set.
	 */
	public static final int SSH_FXF_ACCESS_BLOCK_ADVISORY = 0x00000200;

	/**
	 * If the final component of the path is a symlink, then the open
	 * MUST fail, and the error SSH_FX_LINK_LOOP MUST be returned.
	 */
	public static final int SSH_FXF_ACCESS_NOFOLLOW = 0x00000400;

	/**
	 * The file should be deleted when the last handle to it is closed.
	 * (The last handle may not be an sftp-handle.)  This MAY be emulated
	 * by a server if the OS doesn't support it by deleting the file when
	 * this handle is closed.
	 * <p>
	 * It is implementation specific whether the directory entry is
	 * removed immediately or when the handle is closed.
	 */
	public static final int SSH_FXF_ACCESS_DELETE_ON_CLOSE = 0x00000800;
}

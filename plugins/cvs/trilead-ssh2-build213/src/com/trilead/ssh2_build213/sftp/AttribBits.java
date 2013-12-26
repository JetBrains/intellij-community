
package com.trilead.ssh2_build213.sftp;

/**
 * 
 * SFTP Attribute Bits for the "attrib-bits" and "attrib-bits-valid" fields
 * of the SFTP ATTR data type.
 * <p>
 * Yes, these are the "attrib-bits", even though they have "_FLAGS_" in
 * their name. Don't ask - I did not invent it.
 * <p>
 * "<i>These fields, taken together, reflect various attributes of the file
 * or directory, on the server. Bits not set in 'attrib-bits-valid' MUST be
 * ignored in the 'attrib-bits' field.  This allows both the server and the
 * client to communicate only the bits it knows about without inadvertently
 * twiddling bits they don't understand.</i>"
 *
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: AttribBits.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 *
 */
public class AttribBits
{
	/**
	 * Advisory, read-only bit. This bit is not part of the access
	 * control information on the file, but is rather an advisory field
	 * indicating that the file should not be written.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_READONLY = 0x00000001;

	/**
	 * The file is part of the operating system.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_SYSTEM = 0x00000002;

	/**
	 * File SHOULD NOT be shown to user unless specifically requested.
	 * For example, most UNIX systems SHOULD set this bit if the filename
	 * begins with a 'period'. This bit may be read-only (see section 5.4 of
	 * the SFTP standard draft). Most UNIX systems will not allow this to be
	 * changed.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_HIDDEN = 0x00000004;

	/**
	 * This attribute applies only to directories. This attribute is
	 * always read-only, and cannot be modified. This attribute means
	 * that files and directory names in this directory should be compared
	 * without regard to case.
	 * <p>
	 * It is recommended that where possible, the server's filesystem be
	 * allowed to do comparisons. For example, if a client wished to prompt
	 * a user before overwriting a file, it should not compare the new name
	 * with the previously retrieved list of names in the directory. Rather,
	 * it should first try to create the new file by specifying
	 * SSH_FXF_CREATE_NEW flag. Then, if this fails and returns
	 * SSH_FX_FILE_ALREADY_EXISTS, it should prompt the user and then retry
	 * the create specifying SSH_FXF_CREATE_TRUNCATE.
	 * <p>
	 * Unless otherwise specified, filenames are assumed to be case sensitive.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_CASE_INSENSITIVE = 0x00000008;

	/**
	 * The file should be included in backup / archive operations.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_ARCHIVE = 0x00000010;

	/**
	 * The file is stored on disk using file-system level transparent
	 * encryption. This flag does not affect the file data on the wire
	 * (for either READ or WRITE requests.)
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_ENCRYPTED = 0x00000020;

	/**
	 * The file is stored on disk using file-system level transparent
	 * compression. This flag does not affect the file data on the wire.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_COMPRESSED = 0x00000040;

	/**
	 * The file is a sparse file; this means that file blocks that have
	 * not been explicitly written are not stored on disk. For example, if
	 * a client writes a buffer at 10 M from the beginning of the file,
	 * the blocks between the previous EOF marker and the 10 M offset would
	 * not consume physical disk space.
	 * <p>
	 * Some servers may store all files as sparse files, in which case
	 * this bit will be unconditionally set. Other servers may not have
	 * a mechanism for determining if the file is sparse, and so the file
	 * MAY be stored sparse even if this flag is not set.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_SPARSE = 0x00000080;

	/**
	 * Opening the file without either the SSH_FXF_ACCESS_APPEND_DATA or
	 * the SSH_FXF_ACCESS_APPEND_DATA_ATOMIC flag (see section 8.1.1.3
	 * of the SFTP standard draft) MUST result in an
	 * SSH_FX_INVALID_PARAMETER error.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_APPEND_ONLY = 0x00000100;

	/**
	 * The file cannot be deleted or renamed, no hard link can be created
	 * to this file, and no data can be written to the file.
	 * <p>
	 * This bit implies a stronger level of protection than
	 * SSH_FILEXFER_ATTR_FLAGS_READONLY, the file permission mask or ACLs.
	 * Typically even the superuser cannot write to immutable files, and
	 * only the superuser can set or remove the bit.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_IMMUTABLE = 0x00000200;

	/**
	 * When the file is modified, the changes are written synchronously
	 * to the disk.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_SYNC = 0x00000400;

	/**
	 * The server MAY include this bit in a directory listing or realpath
	 * response. It indicates there was a failure in the translation to UTF-8.
	 * If this flag is included, the server SHOULD also include the
	 * UNTRANSLATED_NAME attribute.
	 */
	public static final int SSH_FILEXFER_ATTR_FLAGS_TRANSLATION_ERR = 0x00000800;

}

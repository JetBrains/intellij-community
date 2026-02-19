import hashlib

try:
    from ..thirdparty import sha1dc  # pytype: disable=import-error

    sha1 = sha1dc.sha1
except (ImportError, AttributeError):
    sha1 = hashlib.sha1

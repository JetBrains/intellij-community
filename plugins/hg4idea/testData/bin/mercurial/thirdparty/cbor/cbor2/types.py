class CBORTag(object):
    """
    Represents a CBOR semantic tag.

    :param int tag: tag number
    :param value: encapsulated value (any object)
    """

    __slots__ = 'tag', 'value'

    def __init__(self, tag, value):
        self.tag = tag
        self.value = value

    def __eq__(self, other):
        if isinstance(other, CBORTag):
            return self.tag == other.tag and self.value == other.value
        return NotImplemented

    def __repr__(self):
        return 'CBORTag({self.tag}, {self.value!r})'.format(self=self)


class CBORSimpleValue(object):
    """
    Represents a CBOR "simple value".

    :param int value: the value (0-255)
    """

    __slots__ = 'value'

    def __init__(self, value):
        if value < 0 or value > 255:
            raise TypeError('simple value too big')
        self.value = value

    def __eq__(self, other):
        if isinstance(other, CBORSimpleValue):
            return self.value == other.value
        elif isinstance(other, int):
            return self.value == other
        return NotImplemented

    def __repr__(self):
        return 'CBORSimpleValue({self.value})'.format(self=self)


class UndefinedType(object):
    __slots__ = ()


#: Represents the "undefined" value.
undefined = UndefinedType()
break_marker = object()

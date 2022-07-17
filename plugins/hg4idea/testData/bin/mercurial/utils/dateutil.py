# util.py - Mercurial utility functions relative to dates
#
#  Copyright 2018 Boris Feld <boris.feld@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import, print_function

import calendar
import datetime
import time

from ..i18n import _
from .. import (
    encoding,
    error,
    pycompat,
)

if pycompat.TYPE_CHECKING:
    from typing import (
        Callable,
        Dict,
        Iterable,
        Optional,
        Tuple,
        Union,
    )

    hgdate = Tuple[float, int]  # (unixtime, offset)

# used by parsedate
defaultdateformats = (
    b'%Y-%m-%dT%H:%M:%S',  # the 'real' ISO8601
    b'%Y-%m-%dT%H:%M',  #   without seconds
    b'%Y-%m-%dT%H%M%S',  # another awful but legal variant without :
    b'%Y-%m-%dT%H%M',  #   without seconds
    b'%Y-%m-%d %H:%M:%S',  # our common legal variant
    b'%Y-%m-%d %H:%M',  #   without seconds
    b'%Y-%m-%d %H%M%S',  # without :
    b'%Y-%m-%d %H%M',  #   without seconds
    b'%Y-%m-%d %I:%M:%S%p',
    b'%Y-%m-%d %H:%M',
    b'%Y-%m-%d %I:%M%p',
    b'%Y-%m-%d',
    b'%m-%d',
    b'%m/%d',
    b'%m/%d/%y',
    b'%m/%d/%Y',
    b'%a %b %d %H:%M:%S %Y',
    b'%a %b %d %I:%M:%S%p %Y',
    b'%a, %d %b %Y %H:%M:%S',  #  GNU coreutils "/bin/date --rfc-2822"
    b'%b %d %H:%M:%S %Y',
    b'%b %d %I:%M:%S%p %Y',
    b'%b %d %H:%M:%S',
    b'%b %d %I:%M:%S%p',
    b'%b %d %H:%M',
    b'%b %d %I:%M%p',
    b'%b %d %Y',
    b'%b %d',
    b'%H:%M:%S',
    b'%I:%M:%S%p',
    b'%H:%M',
    b'%I:%M%p',
)

extendeddateformats = defaultdateformats + (
    b"%Y",
    b"%Y-%m",
    b"%b",
    b"%b %Y",
)


def makedate(timestamp=None):
    # type: (Optional[float]) -> hgdate
    """Return a unix timestamp (or the current time) as a (unixtime,
    offset) tuple based off the local timezone."""
    if timestamp is None:
        timestamp = time.time()
    if timestamp < 0:
        hint = _(b"check your clock")
        raise error.InputError(
            _(b"negative timestamp: %d") % timestamp, hint=hint
        )
    delta = datetime.datetime.utcfromtimestamp(
        timestamp
    ) - datetime.datetime.fromtimestamp(timestamp)
    tz = delta.days * 86400 + delta.seconds
    return timestamp, tz


def datestr(date=None, format=b'%a %b %d %H:%M:%S %Y %1%2'):
    # type: (Optional[hgdate], bytes) -> bytes
    """represent a (unixtime, offset) tuple as a localized time.
    unixtime is seconds since the epoch, and offset is the time zone's
    number of seconds away from UTC.

    >>> datestr((0, 0))
    'Thu Jan 01 00:00:00 1970 +0000'
    >>> datestr((42, 0))
    'Thu Jan 01 00:00:42 1970 +0000'
    >>> datestr((-42, 0))
    'Wed Dec 31 23:59:18 1969 +0000'
    >>> datestr((0x7fffffff, 0))
    'Tue Jan 19 03:14:07 2038 +0000'
    >>> datestr((-0x80000000, 0))
    'Fri Dec 13 20:45:52 1901 +0000'
    """
    t, tz = date or makedate()
    if b"%1" in format or b"%2" in format or b"%z" in format:
        sign = (tz > 0) and b"-" or b"+"
        minutes = abs(tz) // 60
        q, r = divmod(minutes, 60)
        format = format.replace(b"%z", b"%1%2")
        format = format.replace(b"%1", b"%c%02d" % (sign, q))
        format = format.replace(b"%2", b"%02d" % r)
    d = t - tz
    if d > 0x7FFFFFFF:
        d = 0x7FFFFFFF
    elif d < -0x80000000:
        d = -0x80000000
    # Never use time.gmtime() and datetime.datetime.fromtimestamp()
    # because they use the gmtime() system call which is buggy on Windows
    # for negative values.
    t = datetime.datetime(1970, 1, 1) + datetime.timedelta(seconds=d)
    s = encoding.strtolocal(t.strftime(encoding.strfromlocal(format)))
    return s


def shortdate(date=None):
    # type: (Optional[hgdate]) -> bytes
    """turn (timestamp, tzoff) tuple into iso 8631 date."""
    return datestr(date, format=b'%Y-%m-%d')


def parsetimezone(s):
    # type: (bytes) -> Tuple[Optional[int], bytes]
    """find a trailing timezone, if any, in string, and return a
    (offset, remainder) pair"""
    s = pycompat.bytestr(s)

    if s.endswith(b"GMT") or s.endswith(b"UTC"):
        return 0, s[:-3].rstrip()

    # Unix-style timezones [+-]hhmm
    if len(s) >= 5 and s[-5] in b"+-" and s[-4:].isdigit():
        sign = (s[-5] == b"+") and 1 or -1
        hours = int(s[-4:-2])
        minutes = int(s[-2:])
        return -sign * (hours * 60 + minutes) * 60, s[:-5].rstrip()

    # ISO8601 trailing Z
    if s.endswith(b"Z") and s[-2:-1].isdigit():
        return 0, s[:-1]

    # ISO8601-style [+-]hh:mm
    if (
        len(s) >= 6
        and s[-6] in b"+-"
        and s[-3] == b":"
        and s[-5:-3].isdigit()
        and s[-2:].isdigit()
    ):
        sign = (s[-6] == b"+") and 1 or -1
        hours = int(s[-5:-3])
        minutes = int(s[-2:])
        return -sign * (hours * 60 + minutes) * 60, s[:-6]

    return None, s


def strdate(string, format, defaults=None):
    # type: (bytes, bytes, Optional[Dict[bytes, Tuple[bytes, bytes]]]) -> hgdate
    """parse a localized time string and return a (unixtime, offset) tuple.
    if the string cannot be parsed, ValueError is raised."""
    if defaults is None:
        defaults = {}

    # NOTE: unixtime = localunixtime + offset
    offset, date = parsetimezone(string)

    # add missing elements from defaults
    usenow = False  # default to using biased defaults
    for part in (
        b"S",
        b"M",
        b"HI",
        b"d",
        b"mb",
        b"yY",
    ):  # decreasing specificity
        part = pycompat.bytestr(part)
        found = [True for p in part if (b"%" + p) in format]
        if not found:
            date += b"@" + defaults[part][usenow]
            format += b"@%" + part[0]
        else:
            # We've found a specific time element, less specific time
            # elements are relative to today
            usenow = True

    timetuple = time.strptime(
        encoding.strfromlocal(date), encoding.strfromlocal(format)
    )
    localunixtime = int(calendar.timegm(timetuple))
    if offset is None:
        # local timezone
        unixtime = int(time.mktime(timetuple))
        offset = unixtime - localunixtime
    else:
        unixtime = localunixtime + offset
    return unixtime, offset


def parsedate(date, formats=None, bias=None):
    # type: (Union[bytes, hgdate], Optional[Iterable[bytes]], Optional[Dict[bytes, bytes]]) -> hgdate
    """parse a localized date/time and return a (unixtime, offset) tuple.

    The date may be a "unixtime offset" string or in one of the specified
    formats. If the date already is a (unixtime, offset) tuple, it is returned.

    >>> parsedate(b' today ') == parsedate(
    ...     datetime.date.today().strftime('%b %d').encode('ascii'))
    True
    >>> parsedate(b'yesterday ') == parsedate(
    ...     (datetime.date.today() - datetime.timedelta(days=1)
    ...      ).strftime('%b %d').encode('ascii'))
    True
    >>> now, tz = makedate()
    >>> strnow, strtz = parsedate(b'now')
    >>> (strnow - now) < 1
    True
    >>> tz == strtz
    True
    >>> parsedate(b'2000 UTC', formats=extendeddateformats)
    (946684800, 0)
    """
    if bias is None:
        bias = {}
    if not date:
        return 0, 0
    if isinstance(date, tuple):
        if len(date) == 2:
            return date
        else:
            raise error.ProgrammingError(b"invalid date format")
    if not formats:
        formats = defaultdateformats
    date = date.strip()

    if date == b'now' or date == _(b'now'):
        return makedate()
    if date == b'today' or date == _(b'today'):
        date = datetime.date.today().strftime('%b %d')
        date = encoding.strtolocal(date)
    elif date == b'yesterday' or date == _(b'yesterday'):
        date = (datetime.date.today() - datetime.timedelta(days=1)).strftime(
            r'%b %d'
        )
        date = encoding.strtolocal(date)

    try:
        when, offset = map(int, date.split(b' '))
    except ValueError:
        # fill out defaults
        now = makedate()
        defaults = {}
        for part in (b"d", b"mb", b"yY", b"HI", b"M", b"S"):
            # this piece is for rounding the specific end of unknowns
            b = bias.get(part)
            if b is None:
                if part[0:1] in b"HMS":
                    b = b"00"
                else:
                    # year, month, and day start from 1
                    b = b"1"

            # this piece is for matching the generic end to today's date
            n = datestr(now, b"%" + part[0:1])

            defaults[part] = (b, n)

        for format in formats:
            try:
                when, offset = strdate(date, format, defaults)
            except (ValueError, OverflowError):
                pass
            else:
                break
        else:
            raise error.ParseError(
                _(b'invalid date: %r') % pycompat.bytestr(date)
            )
    # validate explicit (probably user-specified) date and
    # time zone offset. values must fit in signed 32 bits for
    # current 32-bit linux runtimes. timezones go from UTC-12
    # to UTC+14
    if when < -0x80000000 or when > 0x7FFFFFFF:
        raise error.ParseError(_(b'date exceeds 32 bits: %d') % when)
    if offset < -50400 or offset > 43200:
        raise error.ParseError(_(b'impossible time zone offset: %d') % offset)
    return when, offset


def matchdate(date):
    # type: (bytes) -> Callable[[float], bool]
    """Return a function that matches a given date match specifier

    Formats include:

    '{date}' match a given date to the accuracy provided

    '<{date}' on or before a given date

    '>{date}' on or after a given date

    >>> p1 = parsedate(b"10:29:59")
    >>> p2 = parsedate(b"10:30:00")
    >>> p3 = parsedate(b"10:30:59")
    >>> p4 = parsedate(b"10:31:00")
    >>> p5 = parsedate(b"Sep 15 10:30:00 1999")
    >>> f = matchdate(b"10:30")
    >>> f(p1[0])
    False
    >>> f(p2[0])
    True
    >>> f(p3[0])
    True
    >>> f(p4[0])
    False
    >>> f(p5[0])
    False
    """

    def lower(date):
        # type: (bytes) -> float
        d = {b'mb': b"1", b'd': b"1"}
        return parsedate(date, extendeddateformats, d)[0]

    def upper(date):
        # type: (bytes) -> float
        d = {b'mb': b"12", b'HI': b"23", b'M': b"59", b'S': b"59"}
        for days in (b"31", b"30", b"29"):
            try:
                d[b"d"] = days
                return parsedate(date, extendeddateformats, d)[0]
            except error.ParseError:
                pass
        d[b"d"] = b"28"
        return parsedate(date, extendeddateformats, d)[0]

    date = date.strip()

    if not date:
        raise error.InputError(
            _(b"dates cannot consist entirely of whitespace")
        )
    elif date[0:1] == b"<":
        if not date[1:]:
            raise error.InputError(_(b"invalid day spec, use '<DATE'"))
        when = upper(date[1:])
        return lambda x: x <= when
    elif date[0:1] == b">":
        if not date[1:]:
            raise error.InputError(_(b"invalid day spec, use '>DATE'"))
        when = lower(date[1:])
        return lambda x: x >= when
    elif date[0:1] == b"-":
        try:
            days = int(date[1:])
        except ValueError:
            raise error.InputError(_(b"invalid day spec: %s") % date[1:])
        if days < 0:
            raise error.InputError(
                _(b"%s must be nonnegative (see 'hg help dates')") % date[1:]
            )
        when = makedate()[0] - days * 3600 * 24
        return lambda x: x >= when
    elif b" to " in date:
        a, b = date.split(b" to ")
        start, stop = lower(a), upper(b)
        return lambda x: x >= start and x <= stop
    else:
        start, stop = lower(date), upper(date)
        return lambda x: x >= start and x <= stop

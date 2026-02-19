/*
 mpatch.c - efficient binary patching for Mercurial

 This implements a patch algorithm that's O(m + nlog n) where m is the
 size of the output and n is the number of patches.

 Given a list of binary patches, it unpacks each into a hunk list,
 then combines the hunk lists with a treewise recursion to form a
 single hunk list. This hunk list is then applied to the original
 text.

 The text (or binary) fragments are copied directly from their source
 Python objects into a preallocated output string to avoid the
 allocation of intermediate Python objects. Working memory is about 2x
 the total number of hunks.

 Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>

 This software may be used and distributed according to the terms
 of the GNU General Public License, incorporated herein by reference.
*/

#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include "bitmanipulation.h"
#include "compat.h"
#include "mpatch.h"

/* VC9 doesn't include bool and lacks stdbool.h based on cext/util.h */
#if defined(_MSC_VER) || __STDC_VERSION__ < 199901L
#define true 1
#define false 0
typedef unsigned char bool;
#else
#include <stdbool.h>
#endif

static struct mpatch_flist *lalloc(ssize_t size)
{
	struct mpatch_flist *a = NULL;

	if (size < 1) {
		size = 1;
	}

	a = (struct mpatch_flist *)malloc(sizeof(struct mpatch_flist));
	if (a) {
		a->base = (struct mpatch_frag *)malloc(
		    sizeof(struct mpatch_frag) * size);
		if (a->base) {
			a->head = a->tail = a->base;
			return a;
		}
		free(a);
	}
	return NULL;
}

void mpatch_lfree(struct mpatch_flist *a)
{
	if (a) {
		free(a->base);
		free(a);
	}
}

static ssize_t lsize(struct mpatch_flist *a)
{
	return a->tail - a->head;
}

/* add helper to add src and *dest iff it won't overflow */
static inline bool safeadd(int src, int *dest)
{
	if ((src > 0) == (*dest > 0)) {
		if (*dest > 0) {
			if (src > (INT_MAX - *dest)) {
				return false;
			}
		} else {
			if (src < (INT_MIN - *dest)) {
				return false;
			}
		}
	}
	*dest += src;
	return true;
}

/* subtract src from dest and store result in dest */
static inline bool safesub(int src, int *dest)
{
	if (((src > 0) && (*dest < INT_MIN + src)) ||
	    ((src < 0) && (*dest > INT_MAX + src))) {
		return false;
	}
	*dest -= src;
	return true;
}

/* move hunks in source that are less cut to dest, compensating
   for changes in offset. the last hunk may be split if necessary.
*/
static int gather(struct mpatch_flist *dest, struct mpatch_flist *src, int cut,
                  int offset)
{
	struct mpatch_frag *d = dest->tail, *s = src->head;
	int postend, c, l;

	while (s != src->tail) {
		int soffset = s->start;
		if (!safeadd(offset, &soffset)) {
			break; /* add would overflow, oh well */
		}
		if (soffset >= cut) {
			break; /* we've gone far enough */
		}

		postend = offset;
		if (!safeadd(s->start, &postend) ||
		    !safeadd(s->len, &postend)) {
			break;
		}
		if (postend <= cut) {
			/* save this hunk */
			int tmp = s->start;
			if (!safesub(s->end, &tmp)) {
				break;
			}
			if (!safeadd(s->len, &tmp)) {
				break;
			}
			if (!safeadd(tmp, &offset)) {
				break; /* add would overflow, oh well */
			}
			*d++ = *s++;
		} else {
			/* break up this hunk */
			c = cut;
			if (!safesub(offset, &c)) {
				break;
			}
			if (s->end < c) {
				c = s->end;
			}
			l = cut - offset - s->start;
			if (s->len < l) {
				l = s->len;
			}

			offset += s->start + l - c;

			d->start = s->start;
			d->end = c;
			d->len = l;
			d->data = s->data;
			d++;
			s->start = c;
			s->len = s->len - l;
			s->data = s->data + l;

			break;
		}
	}

	dest->tail = d;
	src->head = s;
	return offset;
}

/* like gather, but with no output list */
static int discard(struct mpatch_flist *src, int cut, int offset)
{
	struct mpatch_frag *s = src->head;
	int postend, c, l;

	while (s != src->tail) {
		int cmpcut = s->start;
		if (!safeadd(offset, &cmpcut)) {
			break;
		}
		if (cmpcut >= cut) {
			break;
		}

		postend = offset;
		if (!safeadd(s->start, &postend)) {
			break;
		}
		if (!safeadd(s->len, &postend)) {
			break;
		}
		if (postend <= cut) {
			/* do the subtraction first to avoid UB integer overflow
			 */
			int tmp = s->start;
			if (!safesub(s->end, &tmp)) {
				break;
			}
			if (!safeadd(s->len, &tmp)) {
				break;
			}
			if (!safeadd(tmp, &offset)) {
				break;
			}
			s++;
		} else {
			c = cut;
			if (!safesub(offset, &c)) {
				break;
			}
			if (s->end < c) {
				c = s->end;
			}
			l = cut - offset - s->start;
			if (s->len < l) {
				l = s->len;
			}

			offset += s->start + l - c;
			s->start = c;
			s->len = s->len - l;
			s->data = s->data + l;

			break;
		}
	}

	src->head = s;
	return offset;
}

/* combine hunk lists a and b, while adjusting b for offset changes in a/
   this deletes a and b and returns the resultant list. */
static struct mpatch_flist *combine(struct mpatch_flist *a,
                                    struct mpatch_flist *b)
{
	struct mpatch_flist *c = NULL;
	struct mpatch_frag *bh, *ct;
	int offset = 0, post;

	if (a && b) {
		c = lalloc((lsize(a) + lsize(b)) * 2);
	}

	if (c) {

		for (bh = b->head; bh != b->tail; bh++) {
			/* save old hunks */
			offset = gather(c, a, bh->start, offset);

			/* discard replaced hunks */
			post = discard(a, bh->end, offset);

			/* insert new hunk */
			ct = c->tail;
			ct->start = bh->start;
			ct->end = bh->end;
			if (!safesub(offset, &(ct->start)) ||
			    !safesub(post, &(ct->end))) {
				/* It was already possible to exit
				 * this function with a return value
				 * of NULL before the safesub()s were
				 * added, so this should be fine. */
				mpatch_lfree(c);
				c = NULL;
				goto done;
			}
			ct->len = bh->len;
			ct->data = bh->data;
			c->tail++;
			offset = post;
		}

		/* hold on to tail from a */
		memcpy(c->tail, a->head, sizeof(struct mpatch_frag) * lsize(a));
		c->tail += lsize(a);
	}
done:
	mpatch_lfree(a);
	mpatch_lfree(b);
	return c;
}

/* decode a binary patch into a hunk list */
int mpatch_decode(const char *bin, ssize_t len, struct mpatch_flist **res)
{
	struct mpatch_flist *l;
	struct mpatch_frag *lt;
	int pos = 0;

	/* assume worst case size, we won't have many of these lists */
	l = lalloc(len / 12 + 1);
	if (!l) {
		return MPATCH_ERR_NO_MEM;
	}

	lt = l->tail;

	/* We check against len-11 to ensure we have at least 12 bytes
	   left in the patch so we can read our three be32s out of it. */
	while (pos >= 0 && pos < (len - 11)) {
		lt->start = getbe32(bin + pos);
		lt->end = getbe32(bin + pos + 4);
		lt->len = getbe32(bin + pos + 8);
		if (lt->start < 0 || lt->start > lt->end || lt->len < 0) {
			break; /* sanity check */
		}
		if (!safeadd(12, &pos)) {
			break;
		}
		lt->data = bin + pos;
		if (!safeadd(lt->len, &pos)) {
			break;
		}
		lt++;
	}

	if (pos != len) {
		mpatch_lfree(l);
		return MPATCH_ERR_CANNOT_BE_DECODED;
	}

	l->tail = lt;
	*res = l;
	return 0;
}

/* calculate the size of resultant text */
ssize_t mpatch_calcsize(ssize_t len, struct mpatch_flist *l)
{
	ssize_t outlen = 0, last = 0;
	struct mpatch_frag *f = l->head;

	while (f != l->tail) {
		if (f->start < last || f->end > len) {
			return MPATCH_ERR_INVALID_PATCH;
		}
		outlen += f->start - last;
		last = f->end;
		outlen += f->len;
		f++;
	}

	outlen += len - last;
	return outlen;
}

int mpatch_apply(char *buf, const char *orig, ssize_t len,
                 struct mpatch_flist *l)
{
	struct mpatch_frag *f = l->head;
	int last = 0;
	char *p = buf;

	while (f != l->tail) {
		if (f->start < last || f->start > len || f->end > len ||
		    last < 0) {
			return MPATCH_ERR_INVALID_PATCH;
		}
		memcpy(p, orig + last, f->start - last);
		p += f->start - last;
		memcpy(p, f->data, f->len);
		last = f->end;
		p += f->len;
		f++;
	}
	if (last < 0) {
		return MPATCH_ERR_INVALID_PATCH;
	}
	memcpy(p, orig + last, len - last);
	return 0;
}

/* recursively generate a patch of all bins between start and end */
struct mpatch_flist *
mpatch_fold(void *bins, struct mpatch_flist *(*get_next_item)(void *, ssize_t),
            ssize_t start, ssize_t end)
{
	ssize_t len;

	if (start + 1 == end) {
		/* trivial case, output a decoded list */
		return get_next_item(bins, start);
	}

	/* divide and conquer, memory management is elsewhere */
	len = (end - start) / 2;
	return combine(mpatch_fold(bins, get_next_item, start, start + len),
	               mpatch_fold(bins, get_next_item, start + len, end));
}

import sys

from converters import CommentsConverter

comments_con = CommentsConverter()

if __name__ == '__main__':
    path = sys.argv[1]
    comments_con.convert_file(path)

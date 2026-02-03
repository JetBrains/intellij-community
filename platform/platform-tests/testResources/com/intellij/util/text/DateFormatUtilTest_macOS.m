#import <Foundation/Foundation.h>
#include <stdio.h>
#include <stdlib.h>

int main(int argc, const char *argv[]) {
  if (argc != 2) {
    printf("usage: %s <UNIX_seconds>\n", argv[0]);
    return 1;
  }

  long seconds = atol(argv[1]);
  NSDate *date = [NSDate dateWithTimeIntervalSince1970:(NSTimeInterval)seconds];
  NSString *result = [NSDateFormatter localizedStringFromDate:date dateStyle:NSDateFormatterShortStyle timeStyle:NSDateFormatterShortStyle];
  printf("%s\n", [result UTF8String]);
  return 0;
}
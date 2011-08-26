#include <stdio.h>

/**
 * Invoked from {@link GeneralCommandLineTest} as external process.
 */
int main(int argc, char *argv[]) {
  int i;
  printf("=====\n");
  for (i = 1; i < argc; ++i) {
    printf("%s\n", argv[i]);
  }
  printf("=====\n");
  return 0;
}

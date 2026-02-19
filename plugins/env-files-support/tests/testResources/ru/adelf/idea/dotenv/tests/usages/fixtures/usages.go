package fixtures

import "os"

func main() {
	os.Getenv("GO_TEST")
	os.Setenv("GO_TEST2", "1")
}

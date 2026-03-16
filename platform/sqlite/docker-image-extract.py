# https://www.madebymikal.com/quick-hack-extracting-the-contents-of-a-docker-image-to-disk/

# Call me like this:
#  docker-image-extract tarfile.tar extracted

import tarfile
import json
import os
import sys

def is_safe_path(basedir, path):
    base = os.path.abspath(basedir)
    target = os.path.abspath(path)
    return os.path.commonpath([base]) == os.path.commonpath([base, target])
def main():
    if len(sys.argv) < 3:
        print("Usage: python docker-image-extract.py <image.tar> <destination_dir>")
        sys.exit(1)
    image_path = sys.argv[1]
    extracted_path = sys.argv[2]
    if not os.path.exists(extracted_path):
        os.makedirs(extracted_path)
    try:
        image = tarfile.open(image_path)
    except Exception as e:
        print(f"Error opening image: {e}")
        sys.exit(1)
    try:
        manifest_data = image.extractfile('manifest.json')
        if not manifest_data:
            print("Error: manifest.json not found in the image archive.")
            sys.exit(1)
        manifest = json.loads(manifest_data.read())
    except Exception as e:
        print(f"Error reading manifest: {e}")
        sys.exit(1)
    for layer in manifest[0]['Layers']:
        print(f"Found layer: {layer}")
        layer_fileobj = image.extractfile(layer)
        if not layer_fileobj:
            continue
        layer_tar = tarfile.open(fileobj=layer_fileobj)
        for tarinfo in layer_tar:
            dest = os.path.normpath(os.path.join(extracted_path, tarinfo.name))
            if not is_safe_path(extracted_path, dest):
                print(f"  [!] SECURITY WARNING: Skipping unsafe file (Path Traversal): {tarinfo.name}")
                continue
            print(f"  ... {tarinfo.name}")
            if tarinfo.isdev():
                print("  --> skip device files")
                continue
            if not tarinfo.isdir() and os.path.exists(dest):
                if os.path.isfile(dest) or os.path.islink(dest):
                    print("  --> remove old version of file")
                    os.unlink(dest)
            try:
                layer_tar.extract(tarinfo, path=extracted_path)
            except Exception as e:
                print(f"  [!] Error extracting {tarinfo.name}: {e}")
    image.close()
    print("\nExtraction complete.")
if __name__ == "__main__":
    main()
